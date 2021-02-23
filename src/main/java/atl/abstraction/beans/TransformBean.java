package atl.abstraction.beans;

public class TransformBean {

	private String atlModel;
	private String dotAtlModel;
	private String map;
	private String subModel;
	private String trace;
	private Boolean perfectInfo;
	private Boolean imperfectRecall;

	public Boolean getPerfectInfo() {
		return perfectInfo;
	}

	public void setPerfectInfo(Boolean perfectInfo) {
		this.perfectInfo = perfectInfo;
	}

	public Boolean getImperfectRecall() {
		return imperfectRecall;
	}

	public void setImperfectRecall(Boolean imperfectRecall) {
		this.imperfectRecall = imperfectRecall;
	}

	public void setSubModel(String subModel) {
		this.subModel = subModel;
	}

	public String getTrace() {
		return trace;
	}

	public void setTrace(String trace) {
		this.trace = trace;
	}

	public String getSubModel() {
		return subModel;
	}

	public void setJson(String json) {
		this.subModel = json;
	}

	public String getMap() {
		return map;
	}

	public void setMap(String map) {
		this.map = map;
	}

	public String getAtlModel() {
		return atlModel;
	}

	public void setAtlModel(String atlModel) {
		this.atlModel = atlModel;
	}
	
	public String getDotAtlModel() {
		return dotAtlModel;
	}

	public void setDotAtlModel(String dotAtlModel) {
		this.dotAtlModel = dotAtlModel;
	}

}
