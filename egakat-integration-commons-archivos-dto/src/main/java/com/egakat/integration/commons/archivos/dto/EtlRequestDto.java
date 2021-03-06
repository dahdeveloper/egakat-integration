package com.egakat.integration.commons.archivos.dto;

import com.egakat.core.domain.IdentifiedDomainObject;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@ToString(callSuper = true)
public class EtlRequestDto<T extends IdentifiedDomainObject<ID>, ID> {
//
//	private ArchivoDto archivo;
//
//	private TipoArchivoDto tipoArchivo;
//
//	private List<CampoDto> campos;
//
//	private List<LlaveDto> llaves;
//
//	private DirectorioDto directorio;
//
//	private String datos;
//
//	private List<RegistroDto<T, ID>> registros;
//
//	public String getUnescapedSeparadorRegistros() {
//		val result = unescapeJava(tipoArchivo.getSeparadorRegistros());
//		return result;
//	}
//
//	public String getUnescapedSeparadorCampos() {
//		val result = unescapeJava(tipoArchivo.getSeparadorCampos());
//		return result;
//	}
//
//	public String getRegExpSeparadorRegistros() {
//		val result = getRegExpSeparador(tipoArchivo.getSeparadorRegistros());
//		return result;
//	}
//
//	public String getRegExpSeparadorCampos() {
//		val result = getRegExpSeparador(tipoArchivo.getSeparadorCampos());
//		return result;
//	}
//
//	private String getRegExpSeparador(String result) {
//		if (result.startsWith("\\")) {
//			return "[" + result + "]";
//		}
//		return result;
//	}
//
//	public List<CampoDto> getCamposNoIgnorados() {
//		val result = getCampos().stream().filter(a -> !a.isIgnorar()).collect(Collectors.toList());
//		return result;
//	}
//
//	public List<CampoDto> getCamposNoIncluidos() {
//		val result = getCampos().stream().filter(a -> !a.isIncluir()).collect(Collectors.toList());
//		return result;
//	}
//
//	public List<CampoDto> getCamposIncluidos() {
//		val result = getCampos().stream().filter(a -> a.isIncluir()).collect(Collectors.toList());
//		return result;
//	}
//
//	public Optional<CampoDto> getCampo(String codigo) {
//		val result = getCampos().stream().filter(a -> a.getCodigo().equals(codigo)).findFirst();
//		return result;
//	}
//
//	public Path getPathRuta() {
//		String ruta = getArchivo().getRuta();
//		if (ruta == null) {
//			ruta = "";
//		}
//		val result = Paths.get(ruta);
//		return result;
//	}
//
//	public String getSubdirectorioDump() {
//		// TODO Auto-generated method stub
//		return null;
//	}
//
//	public String getSubdirectorioProcesados() {
//		// TODO Auto-generated method stub
//		return null;
//	}
//
//	public String getSubdirectorioErrores() {
//		// TODO Auto-generated method stub
//		return null;
//	}
}
