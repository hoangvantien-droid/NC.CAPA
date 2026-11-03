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

    public Seed getSeed() { return seed; }
    public void setSeed(Seed seed) { this.seed = seed; }
    public Capa getCapa() { return capa; }
    public void setCapa(Capa capa) { this.capa = capa; }
    public Nc getNc() { return nc; }
    public void setNc(Nc nc) { this.nc = nc; }
    public Sod getSod() { return sod; }
    public void setSod(Sod sod) { this.sod = sod; }

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
}
