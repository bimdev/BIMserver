package org.bimserver.emf;

import java.io.OutputStream;
import java.io.PrintWriter;
import java.util.Iterator;
import java.util.List;

import org.apache.commons.codec.binary.Base64;
import org.bimserver.emf.IdEObjectImpl.State;
import org.bimserver.models.ifc2x3tc1.IfcGloballyUniqueId;
import org.bimserver.plugins.serializers.ProgressReporter;
import org.bimserver.plugins.serializers.SerializerException;
import org.bimserver.utils.UTF8PrintWriter;
import org.codehaus.jettison.json.JSONObject;
import org.eclipse.emf.ecore.EReference;
import org.eclipse.emf.ecore.EStructuralFeature;

import com.google.common.base.Charsets;

public class SharedJsonSerializer {
	enum Mode {
		HEADER, BODY, FOOTER, DONE
	}
	
	private static final boolean SERIALIZE_EMPTY_LISTS = false;

	private UTF8PrintWriter out;
	private Mode mode = Mode.HEADER;
	private boolean firstObject = true;
	private Iterator<IdEObject> iterator;

	private IfcModelInterface model;

	public SharedJsonSerializer(IfcModelInterface model) {
		this.model = model;
	}
	
	public boolean write(OutputStream outputStream, ProgressReporter progressReporter) throws SerializerException {
		try {
			if (out == null) {
				out = new UTF8PrintWriter(outputStream);
			}
			if (mode == Mode.HEADER) {
				out.write("{");
				out.write("\"objects\":[");
				mode = Mode.BODY;
				iterator = model.iterator();
				return true;
			} else if (mode == Mode.BODY) {
				if (iterator.hasNext()) {
					IdEObject object = iterator.next();
					if (object.eClass().getEAnnotation("hidden") == null) {
						if (!firstObject) {
							out.write(",");
						} else {
							firstObject = false;
						}
						if (((IdEObjectImpl) object).getLoadingState() != State.LOADED) {
							out.write("{");
							out.write("\"_i\":" + object.getOid() + ",");
							out.write("\"_t\":\"" + object.eClass().getName() + "\",");
							out.write("\"_s\":0");
							out.write("}\n");
						} else {
							out.write("{");
							out.write("\"_i\":" + object.getOid() + ",");
							out.write("\"_t\":\"" + object.eClass().getName() + "\",");
							out.write("\"_s\":1");
							for (EStructuralFeature eStructuralFeature : object.eClass().getEAllStructuralFeatures()) {
								if (eStructuralFeature.getEAnnotation("nolazyload") == null && eStructuralFeature.getEAnnotation("hidden") == null) {
									if (eStructuralFeature instanceof EReference) {
										Object value = object.eGet(eStructuralFeature);
										if (value != null) {
											if (eStructuralFeature.isMany()) {
												List<?> list = (List<?>) value;
												if (SERIALIZE_EMPTY_LISTS || !list.isEmpty()) {
													out.write(",");
													int wrapped = 0;
													int referred = 0;
													for (Object o : list) {
														if (((IdEObject)o).eClass().getEAnnotation("wrapped") != null) {
															// A little tricky, can we assume if one object in this list is embedded, they all are?
															wrapped++;
														} else {
															referred++;
														}
													}
													if (wrapped == 0 && referred != 0) {
														out.write("\"_r" + eStructuralFeature.getName() + "\":[");
													} else if (wrapped != 0 && referred == 0) {
														out.write("\"_e" + eStructuralFeature.getName() + "\":[");
													} else if (wrapped == 0 && referred == 0) {
														// should not happen
													} else {
														// both, this can occur, for example IfcTrimmedCurve.Trim1
														out.write("\"_e" + eStructuralFeature.getName() + "\":[");
													}
													boolean f = true;
													for (Object o : list) {
														if (!f) {
															out.write(",");
														} else {
															f = false;
														}
														IdEObject ref = (IdEObject) o;
														if (ref.getOid() == -1) {
															writeObject(out, ref);
														} else {
															if (wrapped != 0 && referred != 0) {
																// Special situation, where we have to construct an object around the OID to make it distinguishable from embedded objects
																out.write("{");
																out.write("\"i\":");
																out.write("" + ref.getOid());
																out.write("}");
															} else {
																out.write("" + ref.getOid());
															}
														}
													}
													out.write("]");
												}
											} else {
												out.write(",");
												IdEObject ref = (IdEObject) value;
												if (ref instanceof IfcGloballyUniqueId) {
													out.write("\"" + eStructuralFeature.getName() + "\":");
													writePrimitive(out, eStructuralFeature, ((IfcGloballyUniqueId)ref).getWrappedValue());
												} else if (((IdEObject)ref).eClass().getEAnnotation("wrapped") != null) {
													out.write("\"_e" + eStructuralFeature.getName() + "\":");
													writeObject(out, ref);
												} else {
													out.write("\"_r" + eStructuralFeature.getName() + "\":" + ref.getOid());
												}
											}
										}
									} else {
										Object value = object.eGet(eStructuralFeature);
										if (value != null) {
											if (eStructuralFeature.isMany()) {
												List<?> list = (List<?>) value;
												if (SERIALIZE_EMPTY_LISTS || !list.isEmpty()) {
													out.write(",");
													out.write("\"" + eStructuralFeature.getName() + "\":[");
													boolean f = true;
													for (Object o : list) {
														if (!f) {
															out.write(",");
														} else {
															f = false;
														}
														writePrimitive(out, eStructuralFeature, o);
													}
													out.write("]");
												}
											} else {
												out.write(",");
												out.write("\"" + eStructuralFeature.getName() + "\":");
												writePrimitive(out, eStructuralFeature, value);
											}
										}
									}
								}
							}
							out.write("}\n");
						}
					}
					return true;
				} else {
					mode = Mode.FOOTER;
					return true;
				}
			} else if (mode == Mode.FOOTER) {
				out.write("]");
				out.write("}");
				out.flush();
				mode = Mode.DONE;
				return false;
			}
		} catch (Throwable e) {
			throw new SerializerException(e);
		}
		return false;
	}

	private void writeObject(UTF8PrintWriter out, IdEObject object) {
		if (object.eClass().getEAnnotation("wrapped") != null) {
			EStructuralFeature wrappedFeature = object.eClass().getEStructuralFeature("wrappedValue");
			out.write("{");
			out.write("\"_t\":\"" + object.eClass().getName() + "\",");
			out.write("\"_v\":");
			writePrimitive(out, wrappedFeature, object.eGet(wrappedFeature));
			out.write("}");
		} else {
			out.write("" + object.getOid());
		}
	}

	private void writePrimitive(PrintWriter out, EStructuralFeature feature, Object value) {
		if (value instanceof String) {
			out.write(JSONObject.quote((String)value));
		} else if (value instanceof byte[]) {
			out.write("\"" + new String(Base64.encodeBase64((byte[])value), Charsets.UTF_8) + "\"");
		} else if (value instanceof Enum) {
			 if (value.toString().equalsIgnoreCase("true") || value.toString().equalsIgnoreCase("false")) {
				 out.write(value.toString().toLowerCase());
			 } else {
				out.write("\"" + value + "\"");
			 }
		} else {
			out.write("" + value);
		}
	}
}