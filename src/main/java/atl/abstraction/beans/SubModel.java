package atl.abstraction.beans;

public class SubModel {
    private int id;
    private String subJson;
    private String subFormula;
    private String subDot;
    private String modelJson;
    private String modeldot;

    public SubModel(int id, String subJson, String subFormula, String subDot, String modelJson, String modeldot) {
        this.id = id;
        this.subJson = subJson;
        this.subFormula = subFormula;
        this.subDot = subDot;
        this.modelJson = modelJson;
        this.modeldot = modeldot;
    }

    public String getSubJson() {
        return subJson;
    }

    public void setSubJson(String subJson) {
        this.subJson = subJson;
    }

    public String getSubFormula() {
        return subFormula;
    }

    public void setSubFormula(String subFormula) {
        this.subFormula = subFormula;
    }

    public String getSubDot() {
        return subDot;
    }

    public void setSubDot(String subDot) {
        this.subDot = subDot;
    }

    public String getModelJson() {
        return modelJson;
    }

    public void setModelJson(String modelJson) {
        this.modelJson = modelJson;
    }

    public String getModeldot() {
        return modeldot;
    }

    public void setModeldot(String modeldot) {
        this.modeldot = modeldot;
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }
}
