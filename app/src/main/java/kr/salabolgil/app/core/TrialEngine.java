package kr.salabolgil.app.core;

import java.time.Instant;
import static kr.salabolgil.app.core.AppData.*;

/** Deterministic trial state machine. Arrival is deliberately separate from observation. */
public final class TrialEngine {
    public final AppData data;
    private boolean demoDriving;
    private boolean uxKnown;
    private boolean restricted = true;
    private boolean parkedConfirmed;
    private boolean vehicleEnvironment;

    public TrialEngine(AppData data) {
        if (data == null || data.schemaVersion != 1) throw new IllegalArgumentException("지원하지 않는 저장 데이터입니다.");
        this.data = data;
        // A restored in-progress demo starts in the locked state rather than opening the editor.
        Task active = active();
        demoDriving = data.demo && active != null &&
            (active.stage == Stage.REQUESTED || active.stage == Stage.TRAVELLING || active.stage == Stage.ARRIVED);
    }
    public Task active() {
        if (data.activeCandidate < 0 || data.activeCandidate >= data.candidates.length || data.activeTask < 0
                || data.activeTask >= data.candidates[data.activeCandidate].tasks.length) return null;
        return data.candidates[data.activeCandidate].tasks[data.activeTask];
    }
    public boolean canEdit() {
        if (vehicleEnvironment && (!uxKnown || restricted)) return false;
        return data.demo ? !demoDriving : uxKnown && !restricted && parkedConfirmed;
    }
    public boolean isVehicleEnvironment() { return vehicleEnvironment; }
    public void setVehicleEnvironment(boolean value) { vehicleEnvironment = value; parkedConfirmed = false; }
    public boolean isDemoDriving() { return demoDriving; }
    public boolean isUxKnown() { return uxKnown; }
    public boolean isRestricted() { return restricted; }
    public boolean isParkedConfirmed() { return parkedConfirmed; }
    public void updateUx(boolean known, boolean restricted) {
        this.uxKnown = known; this.restricted = restricted;
        if (!known || restricted) parkedConfirmed = false;
    }
    public void confirmParked() {
        require(!vehicleEnvironment || uxKnown && !restricted, "실제 차량의 UX 제한 상태를 확인해야 합니다. 시연으로 우회할 수 없습니다.");
        if (data.demo) { demoDriving = false; return; }
        require(uxKnown && !restricted, "차량의 UX 제한 해제를 확인할 수 없어 입력할 수 없습니다.");
        parkedConfirmed = true;
    }
    public void demoDrive() { require(data.demo, "시연 모드에서만 재현할 수 있습니다."); demoDriving = true; }
    public void begin(int candidate, int task) {
        require(canEdit(), "주차 후에 체험을 시작해 주세요.");
        require(candidate >= 0 && candidate < 2 && task >= 0 && task < 3, "체험 항목을 확인해 주세요.");
        Task previous = active();
        require(previous == null || previous.stage == Stage.RECORDED || previous.stage == Stage.SKIPPED,
            "진행 중인 체험을 기록하거나 건너뛴 뒤 다음 체험을 시작해 주세요.");
        Task next = data.candidates[candidate].tasks[task];
        require(next.stage == Stage.READY, "이미 처리한 과제입니다. 다음 답사를 위해 기록을 초기화할 수 있습니다.");
        require(!next.destination.trim().isEmpty() && next.hasCoordinates(), "목적지 이름과 올바른 좌표를 먼저 입력해 주세요.");
        data.activeCandidate = candidate; data.activeTask = task; data.paused = false;
        next.stage = Stage.REQUESTED; next.arrivalReceived = false; next.arrivalCount = 0;
        parkedConfirmed = false;
        if (data.demo) demoDriving = true;
    }
    public void routeStarted() {
        Task t = active(); if (t != null && t.stage == Stage.REQUESTED) t.stage = Stage.TRAVELLING;
    }
    public void requestFailed() {
        Task t = active(); if (t != null && (t.stage == Stage.REQUESTED || t.stage == Stage.TRAVELLING)) {
            t.stage = Stage.READY; data.activeCandidate = -1; data.activeTask = -1;
            data.paused = false;
            if (data.demo) demoDriving = false;
        }
    }
    public boolean arrive() {
        Task t = active();
        if (t == null || data.paused || t.arrivalReceived ||
                (t.stage != Stage.REQUESTED && t.stage != Stage.TRAVELLING)) return false;
        t.arrivalReceived = true; t.arrivalCount++; t.stage = Stage.ARRIVED; return true;
    }
    public void saveRecord(Integer actualMinutes, Use use, String parking, String access, String note) {
        require(canEdit(), "주차 후에만 기록할 수 있습니다.");
        Task t = active();
        require(t != null && !data.paused && (t.stage == Stage.ARRIVED || t.stage == Stage.TRAVELLING || t.stage == Stage.REQUESTED),
            "진행 중인 체험이 없습니다.");
        require(actualMinutes == null || actualMinutes > 0 && actualMinutes <= 1440, "소요시간은 1~1440분으로 입력해 주세요.");
        require(use != null, "이용 여부를 선택해 주세요.");
        t.actualMinutes = actualMinutes; t.use = use; t.parking = parking; t.access = access; t.note = note;
        t.recordedAt = Instant.now().toString(); t.stage = Stage.RECORDED;
    }
    public void skip() {
        require(canEdit(), "주차 후에 건너뛰어 주세요.");
        Task t = active(); require(t != null && t.stage != Stage.RECORDED && t.stage != Stage.SKIPPED, "진행 중인 체험이 없습니다.");
        t.stage = Stage.SKIPPED; t.use = Use.UNKNOWN; t.actualMinutes = null; data.paused = false;
    }
    public void pause() { require(active() != null, "진행 중인 체험이 없습니다."); data.paused = true; parkedConfirmed = false; }
    public void resume() {
        require(canEdit(), "주차 후에 재개해 주세요."); data.paused = false;
        Task t = active(); if (t != null && t.stage == Stage.TRAVELLING) t.stage = Stage.REQUESTED;
        if (t != null && t.stage == Stage.REQUESTED) { parkedConfirmed = false; if (data.demo) demoDriving = true; }
    }
    public void resetTask(int candidate, int task) {
        require(canEdit(), "주차 후에 초기화해 주세요.");
        Task t = data.candidates[candidate].tasks[task];
        require(t.stage == Stage.RECORDED || t.stage == Stage.SKIPPED || t.stage == Stage.READY, "진행 중인 체험은 먼저 종료해 주세요.");
        t.stage = Stage.READY; t.actualMinutes = null; t.use = Use.UNKNOWN; t.note = ""; t.parking = "미확인";
        t.access = "미확인"; t.recordedAt = ""; t.arrivalReceived = false; t.arrivalCount = 0;
        if (data.activeCandidate == candidate && data.activeTask == task) { data.activeCandidate = -1; data.activeTask = -1; }
    }
    public static String actualText(Task t) { return t.actualMinutes == null ? "미확인" : t.actualMinutes + "분"; }
    public static String difference(Task t) {
        if (!t.hasEstimate() || t.actualMinutes == null) return "차이 미확인";
        int n = t.actualMinutes - t.expectedMinutes;
        return n == 0 ? "예상과 같음" : (n > 0 ? "+" : "") + n + "분";
    }
    public String hospitalFit(Task t) {
        if (!"병원".equals(t.type) || t.actualMinutes == null) return "판단 보류";
        return t.actualMinutes > data.hospitalLimitMinutes ? "내 기준 " + data.hospitalLimitMinutes + "분 초과" : "내 시간 기준 이내";
    }
    public static String useText(Use use) { return use == Use.AVAILABLE ? "이용 가능 확인" : use == Use.UNAVAILABLE ? "이용 불가 확인" : "이용 미확인"; }
    public static String stageText(Stage stage) {
        switch (stage) {
            case REQUESTED: return "길 안내 요청 중";
            case TRAVELLING: return "이동 단계";
            case ARRIVED: return "도착 알림 · 이용 미확인";
            case RECORDED: return "현장 기록 저장됨";
            case SKIPPED: return "건너뜀 · 미확인";
            default: return "체험 전";
        }
    }
    private static void require(boolean condition, String message) { if (!condition) throw new IllegalStateException(message); }
}
