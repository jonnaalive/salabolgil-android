package kr.salabolgil.app.core;

import java.io.Serializable;

/** Only user-entered planning and observation data; vehicle safety state is never persisted. */
public final class AppData implements Serializable {
    private static final long serialVersionUID = 1L;
    public int schemaVersion = 1;
    public boolean demo = true;
    public int commuteDays = 2;
    public int shoppingDays = 1;
    public int hospitalLimitMinutes = 15;
    public int activeCandidate = -1;
    public int activeTask = -1;
    public boolean paused;
    public Candidate[] candidates = {new Candidate("A"), new Candidate("B")};

    public static final class Candidate implements Serializable {
        private static final long serialVersionUID = 1L;
        public final String id;
        public String name;
        public String home = "";
        public Task[] tasks = {new Task("출근"), new Task("병원"), new Task("장보기")};
        Candidate(String id) { this.id = id; this.name = "후보 " + id; }
    }

    public enum Stage { READY, REQUESTED, TRAVELLING, ARRIVED, RECORDED, SKIPPED }
    public enum Use { UNKNOWN, AVAILABLE, UNAVAILABLE }

    public static final class Task implements Serializable {
        private static final long serialVersionUID = 1L;
        public final String type;
        public String destination = "";
        public Double latitude;
        public Double longitude;
        public Integer expectedMinutes;
        public String estimateSource = "";
        public String estimateDate = "";
        public String estimateAssumptions = "";
        public Stage stage = Stage.READY;
        public Integer actualMinutes;
        public Use use = Use.UNKNOWN;
        public String parking = "미확인";
        public String access = "미확인";
        public String note = "";
        public String recordedAt = "";
        public boolean arrivalReceived;
        public int arrivalCount;
        Task(String type) { this.type = type; }
        public boolean hasEstimate() {
            return expectedMinutes != null && expectedMinutes > 0 && !estimateSource.trim().isEmpty()
                && !estimateDate.trim().isEmpty() && !estimateAssumptions.trim().isEmpty();
        }
        public boolean hasCoordinates() {
            return latitude != null && longitude != null && Double.isFinite(latitude) && Double.isFinite(longitude)
                && latitude >= -90 && latitude <= 90 && longitude >= -180 && longitude <= 180;
        }
    }

    public static AppData fresh(boolean demo) {
        AppData d = new AppData(); d.demo = demo;
        if (!demo) return d;
        for (int c = 0; c < 2; c++) {
            Candidate candidate = d.candidates[c];
            candidate.name = "후보 " + candidate.id + " · 예시";
            candidate.home = "" + candidate.id + " 후보 주거지 (가상)";
            for (int t = 0; t < 3; t++) {
                Task task = candidate.tasks[t];
                task.destination = new String[]{"출근 거점", "병원", "마트"}[t] + " (가상)";
                // Fictional demonstration coordinates only. Real mode starts empty.
                task.latitude = 37.50 + c * .02 + t * .005;
                task.longitude = 127.02 + c * .02 + t * .005;
                task.expectedMinutes = new int[]{25, 12, 10}[t];
                task.estimateSource = "시연용 가정값 · 실제 경로 조회 아님";
                task.estimateDate = "시연 시나리오";
                task.estimateAssumptions = "교통·운영시간을 반영하지 않은 가상 수치";
            }
        }
        return d;
    }
    public static AppData empty(boolean demo) { AppData d = new AppData(); d.demo = demo; return d; }
}
