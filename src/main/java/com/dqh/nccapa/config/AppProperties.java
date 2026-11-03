package com.dqh.nccapa.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Cấu hình nghiệp vụ (REQ điểm mở #6, #7; StateMachine §2.2 SoD).
 */
@ConfigurationProperties(prefix = "app")
public class AppProperties {

    private Seed seed = new Seed();
    private Capa capa = new Capa();
    private Nc nc = new Nc();
    private Sod sod = new Sod();
    private Ticket ticket = new Ticket();
    private Upload upload = new Upload();

    public Seed getSeed() { return seed; }
    public void setSeed(Seed seed) { this.seed = seed; }
    public Capa getCapa() { return capa; }
    public void setCapa(Capa capa) { this.capa = capa; }
    public Nc getNc() { return nc; }
    public void setNc(Nc nc) { this.nc = nc; }
    public Sod getSod() { return sod; }
    public void setSod(Sod sod) { this.sod = sod; }
    public Ticket getTicket() { return ticket; }
    public void setTicket(Ticket ticket) { this.ticket = ticket; }
    public Upload getUpload() { return upload; }
    public void setUpload(Upload upload) { this.upload = upload; }

    public static class Seed {
        private boolean enabled = true;
        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
    }

    public static class Capa {
        private Reminder reminder = new Reminder();
        public Reminder getReminder() { return reminder; }
        public void setReminder(Reminder reminder) { this.reminder = reminder; }
        public static class Reminder {
            private int maxPostpone = 2;
            public int getMaxPostpone() { return maxPostpone; }
            public void setMaxPostpone(int maxPostpone) { this.maxPostpone = maxPostpone; }
        }
    }

    public static class Nc {
        private int recurrenceWindowDays = 90;
        public int getRecurrenceWindowDays() { return recurrenceWindowDays; }
        public void setRecurrenceWindowDays(int d) { this.recurrenceWindowDays = d; }
    }

    public static class Sod {
        private boolean enabled = true;
        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
    }

    public static class Ticket {
        /** URL hệ ticket nhận callback. Mặc định trỏ receiver mô phỏng nội bộ. */
        private String callbackUrl = "http://localhost:8080/api/v1/ticket-sim/callback";
        private int maxRetry = 3;
        private int retryDelaySeconds = 60;
        public String getCallbackUrl() { return callbackUrl; }
        public void setCallbackUrl(String v) { this.callbackUrl = v; }
        public int getMaxRetry() { return maxRetry; }
        public void setMaxRetry(int v) { this.maxRetry = v; }
        public int getRetryDelaySeconds() { return retryDelaySeconds; }
        public void setRetryDelaySeconds(int v) { this.retryDelaySeconds = v; }
    }

    public static class Upload {
        /** Thư mục lưu file đính kèm trên disk (Nhóm 2). */
        private String dir = "uploads";
        /** Tổng dung lượng tối đa cho mỗi đối tượng (doiTuong+doiTuongId). Mặc định 50MB. */
        private long maxTotalBytesPerObject = 50L * 1024 * 1024;
        public String getDir() { return dir; }
        public void setDir(String dir) { this.dir = dir; }
        public long getMaxTotalBytesPerObject() { return maxTotalBytesPerObject; }
        public void setMaxTotalBytesPerObject(long v) { this.maxTotalBytesPerObject = v; }
    }
}
