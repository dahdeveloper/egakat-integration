package com.egakat.integration.files.watcher.service.impl;

import static java.nio.file.StandardWatchEventKinds.ENTRY_CREATE;
import static java.nio.file.StandardWatchEventKinds.ENTRY_MODIFY;
import static java.nio.file.StandardWatchEventKinds.OVERFLOW;
import static java.util.stream.Collectors.toList;

import java.io.File;
import java.io.IOException;
import java.nio.file.ClosedWatchServiceException;
import java.nio.file.DirectoryIteratorException;
import java.nio.file.DirectoryStream;
import java.nio.file.DirectoryStream.Filter;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.egakat.integration.files.client.service.api.TipoArchivoLocalService;
import com.egakat.integration.files.dto.ArchivoDto;
import com.egakat.integration.files.dto.DirectorioDto;
import com.egakat.integration.files.enums.EstadoArchivoType;
import com.egakat.integration.files.service.api.ArchivoCrudService;
import com.egakat.integration.files.watcher.service.api.WatchFileSystemService;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import lombok.val;
import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
public class WatchFileSystemServiceImpl implements WatchFileSystemService {

	@Autowired
	private TipoArchivoLocalService tipoArchivoService;

	@Autowired
	private ArchivoCrudService archivoService;

	private WatchService watcher;

	private Map<WatchKey, DirectorioDto> keys;

	@Getter
	@Setter(AccessLevel.PROTECTED)
	private boolean running;

	@Override
	public void start() {
		if (this.setup()) {

			for (; this.isRunning();) {
				waitForSignal();

				if (keys.isEmpty()) {
					this.stop();
				}
			}

		}
	}

	@Override
	public void stop() {
		if (isRunning()) {
			val watchKeys = this.keys.keySet();
			for (WatchKey watchKey : watchKeys) {
				watchKey.cancel();
			}
			this.keys.clear();

			try {
				watcher.close();
			} catch (Exception e) {
				e.printStackTrace();
				;
			}

			setRunning(false);
		}
	}

	protected void reset() throws IOException {
		if(this.watcher != null) {
			try {
				this.watcher.close();
			} catch (IOException e) {
				log.error("this.watcher.close()",e);
			}
		}
		this.watcher = FileSystems.getDefault().newWatchService();
		this.keys = new HashMap<>();
		this.running = false;
	}

	// ---------------------------------------------------------------------------------------------------------------------------------------------------------
	// -- Setup
	// ---------------------------------------------------------------------------------------------------------------------------------------------------------
	protected boolean setup() {
		this.setRunning(false);
		try {
			this.reset();
		} catch (IOException | UnsupportedOperationException e) {
			log.error("Error al intentar obtener un nuevo WatchService. El servicio no se pudo iniciar.",e);
			return false;
		}

		// @formatter:off
		val tiposArchivo = tipoArchivoService.findAllActivos()
				.stream()
				.filter(a -> a.isActivo())
				.collect(toList());
		// @formatter:on

		for (val tipoArchivo : tiposArchivo) {
			log.info("Registrando los directorios del tipo de archivo {}", tipoArchivo.getCodigo());

			val directorio = tipoArchivoService.findOneDirectorioByTipoArchivo(tipoArchivo.getId());
			register(directorio);
		}

		this.setRunning(true);

		return true;
	}

	protected void register(DirectorioDto directorio) {
		if (directorio == null) {
			return;
		}

		log.info("Registrando el directorio {} ", directorio.getSubdirectorioEntradas());
		tryCreateDirectory(directorio.getSubdirectorioEntradas());
		tryCreateDirectory(directorio.getSubdirectorioTemporal());
		tryCreateDirectory(directorio.getSubdirectorioDump());
		tryCreateDirectory(directorio.getSubdirectorioProcesados());
		tryCreateDirectory(directorio.getSubdirectorioErrores());
		tryCreateDirectory(directorio.getSubdirectorioSalidas());

		val path = Paths.get(directorio.getSubdirectorioEntradas());
		processDirectory(directorio, path);

		try {
			WatchKey key = path.register(watcher, ENTRY_CREATE, ENTRY_MODIFY);
			keys.put(key, directorio);
		} catch (IOException e) {
			log.error("No se pudo registrar el directorio {}:", directorio.getSubdirectorioEntradas(), e.getMessage());
		} catch (ClosedWatchServiceException e) {
			throw e;
		}
	}

	private void tryCreateDirectory(String directorio) {
		if ("".equals(directorio)) {
			return;
		}

		Path path = Paths.get(directorio);
		if (!Files.exists(path)) {
			log.info("Creando el directorio {}", directorio);
			try {
				Files.createDirectories(path);
				log.info("Directorio {} creado OK", directorio);
			} catch (IOException e) {
				log.error("No se pudo crear el directorio {}:", directorio, e.getMessage());
			}
		}
	}

	// ---------------------------------------------------------------------------------------------------------------------------------------------------------
	// --
	// ---------------------------------------------------------------------------------------------------------------------------------------------------------
	protected void waitForSignal() {
		val key = waitForKeyToBeSignalled();

		val directorio = keys.get(key);
		if (directorio == null) {
			return;
		}

		val watchable = (Path) key.watchable();

		for (val event : key.pollEvents()) {
			val success = pollEvents(event, watchable, directorio);
			if (!success) {
				break;
			}
		}

		// reset key and remove from set if directory no longer accessible
		boolean valid = key.reset();
		if (!valid) {
			keys.remove(key);
		}
	}

	protected WatchKey waitForKeyToBeSignalled() {
		WatchKey result = null;
		try {
			result = watcher.take();
		} catch (InterruptedException | ClosedWatchServiceException e) {
			log.error("No se pudo obtener la proxima key:", e.getMessage());
		}
		return result;
	}

	protected boolean pollEvents(WatchEvent<?> event, Path watchable, DirectorioDto directorio) {
		@SuppressWarnings("rawtypes")
		WatchEvent.Kind kind = event.kind();

		// TBD - provide example of how OVERFLOW event is handled
		if (kind == OVERFLOW) {
			log.info("OVERFLOW {}", event.count());
			return false;
		}

		// Context for directory entry event is the file name of entry
		WatchEvent<Path> ev = cast(event);
		Path path = watchable.resolve(ev.context());

		if (Files.isDirectory(path)) {
			return true;
		}

		if (kind == ENTRY_MODIFY) {
			log.info("MODIFIED {}", event.count());
		}

		processFile(directorio, path);
		return true;
	}

	@SuppressWarnings("unchecked")
	private static <T> WatchEvent<T> cast(WatchEvent<?> event) {
		return (WatchEvent<T>) event;
	}

	// ---------------------------------------------------------------------------------------------------------------------------------------------------------
	// --
	// ---------------------------------------------------------------------------------------------------------------------------------------------------------
	private static DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd-HHmmss");

	private void processFile(DirectorioDto directorio, Path pathOrigen) {
		val now = LocalDateTime.now().format(formatter);

		String fileName = now + "_" + pathOrigen.getFileName().toString();

		Path pathDestino = Paths.get(directorio.getSubdirectorioTemporal()).resolve(fileName);

		boolean test = test(pathOrigen);
		// -------------------------------------------------------------------------------------------
		if (test) {
			log.info(">>>>>MOVER");
			try {
				Files.move(pathOrigen, pathDestino, StandardCopyOption.REPLACE_EXISTING);
				log.info(">>>>>MOVIDO");

				// @formatter:off
				val model = ArchivoDto
						.builder()
						.id(null)
						.idTipoArchivo(directorio.getIdTipoArchivo())
						.nombre(pathDestino.getFileName().toString())
						.estado(EstadoArchivoType.NO_PROCESADO)
						.ruta(pathDestino.toString())
						.version(0)
						.build();
				// @formatter:on
				archivoService.create(model);
				log.info(">>>>>CREADO");
			} catch (NoSuchFileException e) {
				if (Files.exists(pathDestino)) {
					log.info(">>>>>ARCHIVO MOVIDO PREVIAMENTE");
				} else {
					log.error(">>>>>ARCHIVO ORIGEN REMOVIDO:{}", e.getMessage());
				}
			} catch (IOException e) {
				log.error(">>>>>NO SE PUDO MOVER:{}", e.getMessage());
			}
		} else {
			log.info(">>>>>IGNORAR");
		}

		log.info("...........................................................................................");
	}

	@SuppressWarnings("static-access")
	private boolean test(final Path pathOrigen) {
		boolean result = false;

		File fileOrigen = pathOrigen.toFile();
		LocalDateTime before = getLastModified(fileOrigen.lastModified());
		long sizeBefore = fileOrigen.length();

		log.info("Procesando archivo {}", pathOrigen.toString());
		log.info("Esperando...");
		try {
			Thread.currentThread().sleep(1000 * 1);
		} catch (InterruptedException e) {
			log.error("error:{}", e.getMessage());
		}

		LocalDateTime after = getLastModified(fileOrigen.lastModified());
		long sizeAfter = fileOrigen.length();

		DateTimeFormatter formatter = DateTimeFormatter.ofPattern("HH:mm:ss.SSS");
		log.info(">>>>>{} - {}", before.format(formatter), after.format(formatter));
		log.info(">>>>>{} - {}", sizeBefore, sizeAfter);

		result = before.isEqual(after);

		return result;
	}

	private LocalDateTime getLastModified(long lastModifiedBefore) {
		return Timestamp.from(Instant.ofEpochMilli(lastModifiedBefore)).toLocalDateTime();
	}

	private void processDirectory(DirectorioDto directorio, Path dir) {
		log.debug("Procesando directorio {}", dir.toString());

		try (val stream = getFiles(dir)) {
			for (Path path : stream) {
				try {
					processFile(directorio, path);
				} catch (RuntimeException e) {
					log.error("Ocurrio el siguiente error: {}", e.getMessage());
				}
			}
		} catch (IOException | DirectoryIteratorException e) {
			log.error("Ocurrio el siguiente error: {}", e.getMessage());
			log.error("El tipo de excepción es {}", e.getClass().getName());
		}
	}

	private DirectoryStream<Path> getFiles(Path path) throws IOException {
		if (Files.notExists(path)) {
			throw new RuntimeException(String.format("El directorio {} no existe", path.toAbsolutePath()));
		}
		val result = Files.newDirectoryStream(path, getFilesFilter());
		return result;
	}

	private Filter<Path> getFilesFilter() {
		return new DirectoryStream.Filter<Path>() {
			public boolean accept(Path path) throws IOException {
				return (Files.isRegularFile(path));
			}
		};
	}
}