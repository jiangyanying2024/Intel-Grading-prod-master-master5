package org.grade.bean.response;

public class TemplateGetResponse {
    private int status;
    private String msg;
    private String paperPoint;
    private String tmpImage;

    // Getters and Setters
    public int getStatus() {
        return status;
    }

    public void setStatus(int status) {
        this.status = status;
    }

    public String getMsg() {
        return msg;
    }

    public void setMsg(String msg) {
        this.msg = msg;
    }

    public String getPaperPoint() {
        return paperPoint;
    }

    public void setPaperPoint(String paperPoint) {
        this.paperPoint = paperPoint;
    }

    public String getTmpImage() {
        return tmpImage;
    }

    public void setTmpImage(String tmpImage) {
        this.tmpImage = tmpImage;
    }

    // Builder pattern
    public static TemplateGetResponseBuilder builder() {
        return new TemplateGetResponseBuilder();
    }

    public static class TemplateGetResponseBuilder {
        private int status;
        private String msg;
        private String paperPoint;
        private String tmpImage;

        public TemplateGetResponseBuilder status(int status) {
            this.status = status;
            return this;
        }

        public TemplateGetResponseBuilder msg(String msg) {
            this.msg = msg;
            return this;
        }

        public TemplateGetResponseBuilder paperPoint(String paperPoint) {
            this.paperPoint = paperPoint;
            return this;
        }

        public TemplateGetResponseBuilder tmpImage(String tmpImage) {
            this.tmpImage = tmpImage;
            return this;
        }

        public TemplateGetResponse build() {
            TemplateGetResponse response = new TemplateGetResponse();
            response.setStatus(status);
            response.setMsg(msg);
            response.setPaperPoint(paperPoint);
            response.setTmpImage(tmpImage);
            return response;
        }
    }
}