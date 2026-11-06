package kr.salabolgil.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.InputType;
import android.view.View;
import android.view.WindowManager;
import android.widget.*;
import java.io.IOException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import kr.salabolgil.app.core.AppData;
import kr.salabolgil.app.core.StateStore;
import kr.salabolgil.app.core.TrialEngine;
import kr.salabolgil.app.platform.NavigationBridge;
import static kr.salabolgil.app.core.AppData.*;

/** Native Android proof of concept: no embedded web page, network account or analytics. */
public final class MainActivity extends Activity implements NavigationBridge.Callback {
    private static final int BG = Color.rgb(13, 22, 34), PANEL = Color.rgb(24, 38, 54),
        TEXT = Color.rgb(239, 246, 250), MUTED = Color.rgb(170, 188, 204),
        TEAL = Color.rgb(72, 219, 176), LINE = Color.rgb(50, 72, 91), AMBER = Color.rgb(255, 203, 107);
    private TrialEngine engine;
    private StateStore store;
    private NavigationBridge bridge;
    private LinearLayout body;
    private int page;
    private String message = "";
    private AlertDialog dialog;
    private boolean loading;

    @Override public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(BG); getWindow().setNavigationBarColor(BG);
        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        store = new StateStore(this);
        try { engine = new TrialEngine(store.load()); }
        catch (IOException | RuntimeException e) {
            engine = new TrialEngine(AppData.fresh(true));
            loading = true;
            message = "이전 기록을 읽지 못했습니다. 원본 파일은 보존했습니다. 설정에서 초기화하거나 앱을 다시 열어 주세요.";
        }
        if (savedInstanceState != null) page = savedInstanceState.getInt("page", 0);
        bridge = new NavigationBridge(this, this);
        configureSafety();
        render();
        if (!engine.data.demo || engine.isVehicleEnvironment()) bridge.connect();
    }
    @Override protected void onSaveInstanceState(Bundle out) { super.onSaveInstanceState(out); out.putInt("page", page); }
    @Override protected void onResume() {
        super.onResume();
        if (bridge != null && engine != null && (!engine.data.demo || engine.isVehicleEnvironment())) {
            engine.updateUx(bridge.hasVerifiedUxState(), bridge.isRestricted());
            if (!engine.canEdit() && dialog != null) dialog.dismiss();
            render();
        }
    }
    @Override protected void onDestroy() { if (bridge != null) bridge.close(); super.onDestroy(); }
    @Override public void onBackPressed() {
        if (page != 0) { page = 0; render(); } else super.onBackPressed();
    }
    @Override public void onReady() { runOnUiThread(() -> {
        engine.updateUx(bridge.hasVerifiedUxState(), bridge.isRestricted()); render();
    }); }
    @Override public void onRouteStarted() { runOnUiThread(() -> { engine.routeStarted(); persist(); render(); }); }
    @Override public void onFailure(String reason) { runOnUiThread(() -> {
        if (engine.data.demo) return;
        engine.requestFailed(); message = reason; persist(); render();
    }); }
    @Override public void onArrival() { runOnUiThread(() -> {
        if (!engine.data.demo && engine.arrive()) { persist(); page = 2; render(); }
    }); }
    @Override public void onDisconnected() { runOnUiThread(() -> {
        engine.updateUx(false, true); message = "차량 연결을 확인할 수 없습니다. 상세 입력을 잠갔습니다."; render();
    }); }
    @Override public void onUxRestrictionChanged(boolean restricted) { runOnUiThread(() -> {
        engine.updateUx(bridge.hasVerifiedUxState(), restricted);
        if (!engine.canEdit() && dialog != null) dialog.dismiss();
        render();
    }); }

    private boolean persist() {
        if (loading) { toast("읽지 못한 원본을 보호하고 있습니다. 설정에서 초기화해 주세요."); return false; }
        try { store.save(engine.data); return true; }
        catch (IOException e) { message = "저장 실패: 기기 저장 공간을 확인해 주세요. 앱을 닫기 전에 다시 저장해 주세요."; toast(message); return false; }
    }
    private void configureSafety() {
        engine.setVehicleEnvironment(getPackageManager().hasSystemFeature(PackageManager.FEATURE_AUTOMOTIVE));
        engine.updateUx(bridge.hasVerifiedUxState(), bridge.isRestricted());
    }
    private void act(Runnable action) {
        try { action.run(); persist(); }
        catch (IllegalStateException | IllegalArgumentException e) { message = e.getMessage(); toast(message); }
        render();
    }
    private boolean guard() {
        if (!engine.canEdit()) { toast("주차 상태와 차량 UX 제한을 먼저 확인해 주세요."); return false; }
        if (loading) { toast("설정에서 저장 오류를 먼저 해결해 주세요."); return false; }
        return true;
    }
    private void render() {
        LinearLayout root = column(); root.setBackgroundColor(BG); root.setPadding(dp(20), dp(14), dp(20), dp(8));
        root.addView(text("살아볼길", 24, TEAL, true));
        TextView badge = text(engine.data.demo ? "시연 모드 · 가상 후보지와 예시 기록" : "실제 체험 모드 · 주차 후 조작", 13, AMBER, false);
        badge.setPadding(0, dp(4), 0, dp(6)); root.addView(badge);
        if (!message.isEmpty()) {
            TextView info = text(message, 14, AMBER, false); info.setPadding(0, 0, 0, dp(8)); root.addView(info);
        }
        LinearLayout tabs = row();
        String[] labels = wide()
            ? new String[]{"시작", "내 생활", "오늘 체험", "후보 비교", "설정"}
            : new String[]{"시작", "생활", "체험", "비교", "설정"};
        for (int i = 0; i < labels.length; i++) {
            final int tab = i;
            Button b = button(labels[i], () -> { page = tab; message = ""; render(); }, page == i);
            b.setTextSize(wide() ? 20 : 14); tabs.addView(b, weight());
        }
        root.addView(tabs);
        ScrollView scroll = new ScrollView(this); scroll.setFillViewport(false);
        body = column(); body.setPadding(0, dp(12), 0, dp(20));
        scroll.addView(body); root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        setContentView(root);
        boolean locked = !engine.canEdit();
        if (locked && page != 2 && page != 4) { lockedView(); return; }
        switch (page) {
            case 1: lifeView(); break;
            case 2: todayView(); break;
            case 3: compareView(); break;
            case 4: settingsView(); break;
            default: homeView();
        }
    }
    private void homeView() {
        heading("이사 후보지에서\n내 생활을 미리 해봅니다");
        paragraph("계약하기 전에, 평소의 출근·병원·장보기를 직접 해보세요. 예상과 현장 기록을 비교하고 남은 질문을 확인합니다.");
        LinearLayout intro = card();
        intro.addView(text("내 생활 → 직접 체험 → 같은 기준으로 비교", 20, TEAL, true));
        intro.addView(text("한 번의 드라이브로 결론 내리지 않습니다. 출근은 평일 출근 시간대에, 시설은 이용 예정 시간에 확인하세요.", 15, MUTED, false));
        body.addView(intro);
        LinearLayout candidates = responsiveRow();
        for (int c = 0; c < 2; c++) {
            final int index = c; Candidate candidate = engine.data.candidates[c];
            LinearLayout item = card(); item.addView(text(candidate.name, 22, TEXT, true));
            item.addView(text(candidate.home.isEmpty() ? "후보 주거지 출발지를 설정하세요" : candidate.home, 15, MUTED, false));
            int count = 0; for (Task t : candidate.tasks) if (t.stage == Stage.RECORDED) count++;
            item.addView(text("생활 과제 " + count + "/3 기록", 16, TEAL, true));
            item.addView(button("이 후보지 체험", () -> { page = 2; render(); }, true));
            item.addView(button("후보지 편집", () -> editCandidate(index), false));
            addResponsive(candidates, item);
        }
        body.addView(candidates);
        body.addView(button("내 생활 조건부터 설정", () -> { page = 1; render(); }, true));
        Task active = engine.active();
        if (active != null) paragraph("저장된 체험: " + TrialEngine.stageText(active.stage) + " · 앱을 다시 열어도 이어집니다.");
        if (engine.data.demo) paragraph("현재 값과 좌표는 시연용입니다. 실제 경로 조회·실차 검증 결과가 아닙니다.");
    }
    private void lifeView() {
        heading("내 평소 생활을 기준으로");
        paragraph("두 후보지에 같은 조건을 적용합니다. 시간 기준을 충족해도 시설 이용이 가능한지는 따로 확인합니다.");
        LinearLayout profile = card();
        profile.addView(text("출근 주 " + engine.data.commuteDays + "회 · 장보기 주 " + engine.data.shoppingDays + "회", 22, TEXT, true));
        profile.addView(text("병원 이동 희망: 차로 " + engine.data.hospitalLimitMinutes + "분 이내", 20, TEAL, true));
        profile.addView(button("생활 조건 편집", this::editProfile, true)); body.addView(profile);
        for (int c = 0; c < 2; c++) {
            final int index = c; Candidate candidate = engine.data.candidates[c];
            LinearLayout box = card(); box.addView(text(candidate.name, 22, TEXT, true));
            box.addView(text(candidate.home.isEmpty() ? "출발지 미설정" : candidate.home, 15, MUTED, false));
            box.addView(button("후보지 이름·출발지", () -> editCandidate(index), false));
            for (int t = 0; t < 3; t++) {
                final int taskIndex = t; Task task = candidate.tasks[t];
                box.addView(text(task.type + " · " + (task.destination.isEmpty() ? "목적지 미설정" : task.destination), 18, TEXT, true));
                box.addView(text(estimateText(task), 14, MUTED, false));
                box.addView(button("목적지·예상값 편집", () -> editTask(index, taskIndex), false));
            }
            body.addView(box);
        }
    }
    private void todayView() {
        heading("오늘은 생활을 직접 확인");
        Task active = engine.active();
        if (!engine.canEdit()) {
            lockedView();
            return;
        }
        if (active != null && active.stage != Stage.RECORDED && active.stage != Stage.SKIPPED) {
            activeView(active); return;
        }
        if (active != null && active.stage == Stage.RECORDED) {
            LinearLayout saved = card(); saved.addView(text("현장 기록을 저장했습니다", 22, TEAL, true));
            saved.addView(text(active.type + " · " + TrialEngine.actualText(active) + " · " + TrialEngine.useText(active.use), 18, TEXT, false));
            saved.addView(text("예상 " + (active.hasEstimate() ? active.expectedMinutes + "분" : "미확인") + " / 기록 " + TrialEngine.actualText(active) + " / " + TrialEngine.difference(active), 18, TEXT, true));
            saved.addView(text(engine.hospitalFit(active), 17, AMBER, true));
            saved.addView(button("예상과 실제·후보지 비교", () -> { page = 3; render(); }, true));
            final int currentCandidate = engine.data.activeCandidate;
            Task[] remaining = engine.data.candidates[currentCandidate].tasks;
            for (int step = 1; step <= remaining.length; step++) {
                final int next = (engine.data.activeTask + step) % remaining.length;
                if (remaining[next].stage == Stage.READY) {
                    saved.addView(button("다음 과제: " + remaining[next].type + " 체험", () -> confirmStart(currentCandidate, next), true));
                    break;
                }
            }
            body.addView(saved);
        }
        paragraph("출근 → 병원 → 장보기 순서입니다. 운영시간과 출근 시간대에 맞춰 필요한 과제부터 선택하거나 건너뛸 수 있습니다. 다음 목적지는 선택 후 확인해야 시작합니다.");
        for (int c = 0; c < 2; c++) {
            final int candidateIndex = c; Candidate candidate = engine.data.candidates[c];
            LinearLayout box = card(); box.addView(text(candidate.name, 23, TEXT, true));
            box.addView(text("출발: " + (candidate.home.isEmpty() ? "미설정" : candidate.home), 15, MUTED, false));
            for (int t = 0; t < 3; t++) {
                final int taskIndex = t; Task task = candidate.tasks[t];
                box.addView(text((t + 1) + ". " + task.type + " · " + (task.destination.isEmpty() ? "목적지 미설정" : task.destination), 19, TEXT, true));
                box.addView(text(TrialEngine.stageText(task.stage) + " · " + estimateText(task), 14, MUTED, false));
                if (task.stage == Stage.READY) box.addView(button(task.type + " 체험 시작", () -> confirmStart(candidateIndex, taskIndex), true));
                else box.addView(button("다음 답사를 위해 이 기록 초기화", () -> confirmResetTask(candidateIndex, taskIndex), false));
            }
            body.addView(box);
        }
    }
    private void activeView(Task task) {
        LinearLayout box = card();
        box.addView(text(engine.data.candidates[engine.data.activeCandidate].name + " · " + task.type, 23, TEXT, true));
        box.addView(text(task.destination, 22, TEAL, true));
        box.addView(text(TrialEngine.stageText(task.stage), 18, TEXT, true));
        if (engine.data.paused) {
            box.addView(text("중단한 체험입니다. 주차 후 이어갈 수 있습니다.", 16, MUTED, false));
            box.addView(button("체험 재개", () -> act(() -> {
                engine.resume();
                if (task.stage == Stage.REQUESTED) {
                    if (engine.data.demo) engine.routeStarted();
                    else bridge.navigate(task.destination, task.latitude, task.longitude);
                }
            }), true));
        } else {
            box.addView(text(task.arrivalReceived ? "내비 도착은 이용 완료가 아닙니다. 주차 후 이용 여부를 직접 기록하세요." : "도착 알림이 없어도 주차 후 직접 확인해 기록할 수 있습니다.", 16, MUTED, false));
            box.addView(button("주차 후 현장 기록", this::recordDialog, true));
            box.addView(button("이 과제 건너뛰기 · 미확인으로 남김", () -> act(() -> { engine.skip(); if (!engine.data.demo) bridge.cancel(); }), false));
            box.addView(button("체험 중단", () -> act(() -> { engine.pause(); if (!engine.data.demo) bridge.cancel(); }), false));
        }
        body.addView(box);
    }
    private void lockedView() {
        Task active = engine.active();
        boolean actualBlocked = engine.isVehicleEnvironment() && (!engine.isUxKnown() || engine.isRestricted());
        heading(actualBlocked ? "차량 UX 제한으로 조작을 잠갔습니다" : engine.data.demo ? "주행 상태를 재현하고 있습니다" : "운전 중에는 길 안내에 집중하세요");
        if (active != null) {
            LinearLayout box = card(); box.addView(text("현재 과제 · " + active.type, 20, MUTED, true));
            box.addView(text(active.destination, 30, TEAL, true));
            box.addView(text(TrialEngine.stageText(active.stage), 18, TEXT, true));
            box.addView(text("도착 알림과 이용 완료는 별개입니다.", 16, MUTED, false)); body.addView(box);
        }
        paragraph(actualBlocked ? "실제 차량의 UX 제한이 적용 중이거나 상태를 확인하지 못했습니다. 시연 모드도 이 제한을 우회하지 않습니다." : engine.data.demo ? "실제 차량 상태가 아닌 시연입니다. 상세 입력을 잠근 동작을 확인할 수 있습니다." : !engine.isUxKnown() ? "차량 UX 제한 상태를 확인할 수 없어 상세 입력과 비교 화면을 잠갔습니다." : engine.isRestricted() ? "차량 UX 제한이 적용 중입니다. 상세 입력과 비교 화면을 잠갔습니다." : "UX 제한 해제만으로 주차를 판단하지 않습니다. 안전한 장소에 주차한 뒤 확인해 주세요.");
        if (engine.data.demo && !actualBlocked) {
            if (active != null && !active.arrivalReceived && (active.stage == Stage.TRAVELLING || active.stage == Stage.REQUESTED))
                body.addView(button("도착 알림 재현", () -> act(() -> engine.arrive()), false));
            body.addView(button("주차 후 기록 상태 재현", () -> act(() -> engine.confirmParked()), true));
        } else if (!engine.data.demo && engine.isUxKnown() && !engine.isRestricted()) {
            body.addView(button("안전하게 주차했습니다", () -> act(() -> engine.confirmParked()), true));
        }
        if (!engine.data.demo) paragraph(bridge.description());
    }
    private void compareView() {
        heading("같은 생활 조건으로 비교합니다");
        paragraph("확인하지 않은 조건은 미확인입니다. 총점이나 자동 추천 순위를 만들지 않습니다.");
        for (int t = 0; t < 3; t++) {
            TextView title = text(new String[]{"출근", "병원", "장보기"}[t], 24, TEXT, true); title.setPadding(0, dp(12), 0, dp(6)); body.addView(title);
            LinearLayout columns = responsiveRow();
            for (int c = 0; c < 2; c++) {
                Candidate candidate = engine.data.candidates[c]; Task task = candidate.tasks[t];
                LinearLayout box = card(); box.addView(text(candidate.name, 20, TEAL, true));
                box.addView(text(task.destination.isEmpty() ? "목적지 미설정" : task.destination, 15, MUTED, false));
                box.addView(text("예상 " + (task.hasEstimate() ? task.expectedMinutes + "분" : "미확인"), 19, TEXT, true));
                box.addView(text((engine.data.demo ? "기록 예시 " : "현장 기록 ") + TrialEngine.actualText(task), 24, TEXT, true));
                box.addView(text(TrialEngine.difference(task), 23, task.actualMinutes == null ? MUTED : AMBER, true));
                box.addView(text(t == 1 ? engine.hospitalFit(task) : "동일 시간대에 다시 확인하세요", 16, AMBER, true));
                box.addView(text(TrialEngine.useText(task.use) + "\n주차: " + task.parking + "\n접근: " + task.access, 16, TEXT, false));
                box.addView(text(TrialEngine.stageText(task.stage), 14, MUTED, false));
                if (!task.note.isEmpty()) box.addView(text("메모: " + task.note, 15, MUTED, false));
                if (!task.recordedAt.isEmpty()) box.addView(text("기록 시점: " + task.recordedAt, 12, MUTED, false));
                if (task.hasEstimate()) box.addView(text("예상 근거: " + task.estimateSource + "\n기준: " + task.estimateDate + "\n가정: " + task.estimateAssumptions, 13, MUTED, false));
                addResponsive(columns, box);
            }
            body.addView(columns);
        }
        body.addView(button("남은 생활 과제 확인", () -> { page = 2; render(); }, true));
    }
    private void settingsView() {
        heading("기록과 실행 환경");
        paragraph("생활 조건·후보지·현장 기록은 앱 내부 저장소에만 보관합니다. 서버 전송·회원가입·사용 분석은 없으며 병명은 수집하지 않습니다. 아래에서 전체 기록을 삭제할 수 있습니다.");
        LinearLayout box = card(); box.addView(text(engine.data.demo ? "시연 모드" : "실제 체험 모드", 23, TEAL, true));
        box.addView(text(engine.data.demo ? "후보지·좌표·예상값은 가상입니다. 입력한 현장 기록도 시연 기록으로 표시합니다." : "직접 확인한 후보지와 목적지를 입력하세요. 차량 연결과 UX 제한 상태를 확인해야 조작할 수 있습니다.", 16, TEXT, false));
        box.addView(text(bridge.description(), 15, MUTED, false));
        box.addView(text("UX 제한 확인: " + (engine.isUxKnown() ? engine.isRestricted() ? "제한 중" : "제한 해제" : "확인 불가") + "\n주차 확인: " + (engine.isParkedConfirmed() ? "사용자 확인" : "확인 전"), 15, MUTED, false));
        if (!engine.data.demo && engine.isUxKnown() && !engine.isRestricted() && !engine.isParkedConfirmed())
            box.addView(button("안전하게 주차했습니다", () -> act(() -> engine.confirmParked()), true));
        box.addView(button("차량 연결 다시 확인", () -> { bridge.close(); bridge = new NavigationBridge(this, this); bridge.connect(); }, false));
        if (engine.data.demo) box.addView(button("주행 상태 재현", () -> act(() -> { engine.demoDrive(); page = 2; }), false));
        if (!engine.isVehicleEnvironment() || engine.isUxKnown() && !engine.isRestricted())
            box.addView(button(engine.data.demo ? "실제 체험 모드로 전환" : "시연 모드로 전환", this::switchMode, false));
        body.addView(box);
        if (engine.canEdit() || loading) body.addView(button("이 기기의 모든 생활 조건·기록 삭제", this::deleteAll, false));
        if (engine.data.demo && engine.canEdit()) body.addView(button("시연 예시 데이터 다시 불러오기", this::reloadDemo, false));
        paragraph("앱은 현재 개발 시제품입니다. 지도·경로 예상시간을 자체 조회하지 않습니다. 예상을 입력하려면 출처·기준 시점·가정을 함께 남겨야 합니다. 실제 차량에서 검증한 결과가 아닙니다.");
    }
    private void editProfile() {
        if (!guard()) return;
        LinearLayout form = form();
        EditText commute = field(form, "주당 출근 횟수 (0~7)", "" + engine.data.commuteDays, true);
        EditText shopping = field(form, "주당 장보기 횟수 (0~7)", "" + engine.data.shoppingDays, true);
        EditText hospital = field(form, "병원 이동 희망 시간 (분, 1~240)", "" + engine.data.hospitalLimitMinutes, true);
        showForm("내 생활 조건", form, () -> {
            int a = integer(commute, 0, 7), b = integer(shopping, 0, 7), c = integer(hospital, 1, 240);
            engine.data.commuteDays = a; engine.data.shoppingDays = b; engine.data.hospitalLimitMinutes = c;
        });
    }
    private void editCandidate(int index) {
        if (!guard()) return; Candidate candidate = engine.data.candidates[index];
        LinearLayout form = form(); EditText name = field(form, "후보지 이름", candidate.name, false);
        EditText home = field(form, "후보 주거지·출발지 메모", candidate.home, false);
        showForm("후보 " + candidate.id + " 편집", form, () -> {
            requireText(name, "후보지 이름"); candidate.name = value(name); candidate.home = value(home);
        });
    }
    private void editTask(int candidate, int taskIndex) {
        if (!guard()) return; Task task = engine.data.candidates[candidate].tasks[taskIndex];
        if (task.stage != Stage.READY) { toast("진행·저장된 과제의 근거를 덮어쓰지 않습니다. 다음 답사용으로 먼저 초기화해 주세요."); return; }
        LinearLayout form = form();
        EditText name = field(form, "목적지 이름", task.destination, false);
        EditText latitude = coordinateField(form, "위도 (-90~90)", task.latitude);
        EditText longitude = coordinateField(form, "경도 (-180~180)", task.longitude);
        EditText expected = field(form, "예상 이동시간 (분, 모르면 비움)", task.expectedMinutes == null ? "" : task.expectedMinutes.toString(), true);
        EditText source = field(form, "예상값 출처·제공처 (예: 직접 확인한 지도 앱)", task.estimateSource, false);
        EditText date = field(form, "예상값 확인 일시", task.estimateDate.isEmpty() ? LocalDate.now().toString() : task.estimateDate, false);
        EditText assumptions = field(form, "예상값의 조건·가정 (출발지·시간대 등)", task.estimateAssumptions, false);
        showForm(task.type + " 목적지와 예상 근거", form, () -> {
            requireText(name, "목적지 이름"); Double lat = decimal(latitude, -90, 90), lon = decimal(longitude, -180, 180);
            Integer minutes = value(expected).isEmpty() ? null : integer(expected, 1, 1440);
            if (minutes != null) { requireText(source, "예상 출처"); requireText(date, "확인 일시"); requireText(assumptions, "예상 조건"); }
            task.destination = value(name); task.latitude = lat; task.longitude = lon; task.expectedMinutes = minutes;
            task.estimateSource = value(source); task.estimateDate = value(date); task.estimateAssumptions = value(assumptions);
        });
    }
    private void confirmStart(int candidate, int task) {
        if (!guard()) return;
        if (!engine.data.demo && (!bridge.isConnected() || !bridge.hasVerifiedUxState())) { toast("차량 내비 연결과 UX 상태를 먼저 확인해 주세요."); return; }
        Task target = engine.data.candidates[candidate].tasks[task];
        if (engine.data.candidates[candidate].home.trim().isEmpty()) { toast("내 생활에서 후보 주거지 출발지를 먼저 입력해 주세요."); return; }
        confirm("이 목적지로 체험할까요?", engine.data.candidates[candidate].name + "\n" + target.type + " · " + target.destination + "\n" + (engine.data.demo ? "가상 이동을 재현합니다. 실제 내비는 실행하지 않습니다." : "현재 위치에서 내비 안내를 요청합니다. 후보 주거지에서 출발하는지 확인하세요."), () -> {
            if (!guard()) return;
            act(() -> {
                engine.begin(candidate, task);
                if (engine.data.demo) engine.routeStarted();
                else bridge.navigate(target.destination, target.latitude, target.longitude);
                page = 2;
            });
        });
    }
    private void recordDialog() {
        if (!guard()) return; Task task = engine.active(); if (task == null) return;
        LinearLayout form = form();
        form.addView(text(engine.data.demo ? "시연용 현장 기록입니다. 실제 측정 결과로 사용하지 마세요." : "안전한 장소에 주차한 뒤 직접 확인한 내용만 남겨 주세요.", 15, AMBER, false));
        EditText actual = field(form, "직접 확인한 이동시간 (분, 모르면 비움)", task.actualMinutes == null ? "" : task.actualMinutes.toString(), true);
        Spinner use = choice(form, "시설 이용 여부", new String[]{"미확인", "이용 가능 확인", "이용 불가 확인"});
        Spinner parking = choice(form, "주차 여건", new String[]{"미확인", "주차 가능 확인", "주차 어려움 확인"});
        Spinner access = choice(form, "입구 접근성", new String[]{"미확인", "접근 가능 확인", "접근 불편 확인"});
        EditText note = field(form, "현장 메모 (시간대·확인 방법·다시 확인할 일)", task.note, false);
        form.addView(text("운영시간만으로 실제 접수·이용이 가능하다고 판단하지 마세요.", 14, MUTED, false));
        showForm("주차 후 " + task.type + " 기록", form, () -> {
            engine.saveRecord(value(actual).isEmpty() ? null : integer(actual, 1, 1440),
                new Use[]{Use.UNKNOWN, Use.AVAILABLE, Use.UNAVAILABLE}[use.getSelectedItemPosition()],
                parking.getSelectedItem().toString(), access.getSelectedItem().toString(), value(note));
            if (!engine.data.demo) bridge.cancel();
        });
    }
    private void confirmResetTask(int candidate, int task) {
        if (!guard()) return;
        confirm("이 과제 기록을 초기화할까요?", "현장 기록과 이용 여부를 지우고 다음 답사를 준비합니다. 목적지와 예상 근거는 유지합니다.", () -> act(() -> engine.resetTask(candidate, task)));
    }
    private void switchMode() {
        if (engine.isVehicleEnvironment() && (!engine.isUxKnown() || engine.isRestricted())) { toast("차량 UX 제한 중에는 모드를 전환할 수 없습니다."); return; }
        confirm("모드를 전환할까요?", "안전한 곳에 주차한 상태에서만 진행하세요. 시연 기록과 실제 기록이 섞이지 않도록 기존 조건·후보지·기록을 모두 삭제합니다.", () -> {
            if (engine.isVehicleEnvironment() && (!engine.isUxKnown() || engine.isRestricted())) return;
            bridge.cancel();
            store.delete(); loading = false; engine = new TrialEngine(AppData.fresh(!engine.data.demo)); page = 0; message = ""; persist();
            configureSafety(); if (!engine.data.demo || engine.isVehicleEnvironment()) bridge.connect(); render();
        });
    }
    private void deleteAll() {
        confirm("모든 기록을 삭제할까요?", "이 기기에 저장된 생활 조건·후보지·현장 기록을 삭제합니다. 되돌릴 수 없습니다.", () -> {
            if (!engine.canEdit()) { toast("주차 후 삭제해 주세요."); return; }
            bridge.cancel();
            boolean demo = engine.data.demo; store.delete(); loading = false; engine = new TrialEngine(AppData.empty(demo)); page = 0; message = "모든 사용자 기록을 삭제했습니다. 후보지와 현장 기록은 비어 있습니다.";
            configureSafety(); persist(); render();
        });
    }
    private void reloadDemo() {
        if (!guard()) return;
        confirm("예시 데이터를 다시 불러올까요?", "현재 시연 기록을 삭제하고 가상 후보 A·B와 시연용 예상값을 불러옵니다.", () -> {
            if (!guard()) return;
            bridge.cancel(); engine = new TrialEngine(AppData.fresh(true)); configureSafety(); message = "시연 예시 데이터를 불러왔습니다."; persist(); render();
        });
    }
    private void showForm(String title, LinearLayout form, Runnable save) {
        ScrollView scroll = new ScrollView(this); scroll.addView(form);
        dialog = new AlertDialog.Builder(this).setTitle(title).setView(scroll).setNegativeButton("취소", null).setPositiveButton("저장", null).create();
        dialog.setOnShowListener(d -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            if (!guard()) { dialog.dismiss(); return; }
            try { save.run(); if (persist()) { dialog.dismiss(); message = "저장했습니다. 이 기기에만 보관됩니다."; render(); } }
            catch (IllegalArgumentException | IllegalStateException e) { toast(e.getMessage()); }
        })); dialog.show();
    }
    private void confirm(String title, String text, Runnable yes) {
        dialog = new AlertDialog.Builder(this).setTitle(title).setMessage(text).setNegativeButton("취소", null).setPositiveButton("확인", (d,w) -> yes.run()).create(); dialog.show();
    }
    private LinearLayout form() { LinearLayout v = column(); v.setPadding(dp(22), dp(8), dp(22), dp(16)); return v; }
    private EditText field(LinearLayout form, String label, String initial, boolean numeric) {
        form.addView(text(label, 15, MUTED, false));
        EditText input = new EditText(this); input.setText(initial); input.setTextColor(TEXT); input.setHintTextColor(MUTED); input.setTextSize(wide() ? 25 : 18);
        input.setSingleLine(); input.setPadding(dp(8), dp(10), dp(8), dp(10));
        input.setInputType(numeric ? InputType.TYPE_CLASS_NUMBER : InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        input.setBackground(tint(PANEL)); LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2); lp.bottomMargin = dp(14); form.addView(input, lp);
        return input;
    }
    private EditText coordinateField(LinearLayout form, String label, Double initial) {
        EditText input = field(form, label, initial == null ? "" : initial.toString(), false);
        input.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL | InputType.TYPE_NUMBER_FLAG_SIGNED); return input;
    }
    private Spinner choice(LinearLayout form, String label, String[] values) {
        form.addView(text(label, 15, MUTED, false)); Spinner spinner = new Spinner(this);
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, values); spinner.setAdapter(adapter);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, dp(54)); lp.bottomMargin = dp(12); form.addView(spinner, lp); return spinner;
    }
    private static String value(EditText field) { return field.getText().toString().trim(); }
    private static void requireText(EditText field, String label) { if (value(field).isEmpty()) throw new IllegalArgumentException(label + "을 입력해 주세요."); }
    private static int integer(EditText field, int min, int max) {
        try { int n = Integer.parseInt(value(field)); if (n < min || n > max) throw new NumberFormatException(); return n; }
        catch (NumberFormatException e) { throw new IllegalArgumentException(min + "~" + max + " 사이 정수를 입력해 주세요."); }
    }
    private static Double decimal(EditText field, int min, int max) {
        if (value(field).isEmpty()) return null;
        try { double n = Double.parseDouble(value(field)); if (!Double.isFinite(n) || n < min || n > max) throw new NumberFormatException(); return n; }
        catch (NumberFormatException e) { throw new IllegalArgumentException("좌표 범위를 확인해 주세요: " + min + "~" + max); }
    }
    private String estimateText(Task task) { return task.hasEstimate() ? "예상 " + task.expectedMinutes + "분 · " + task.estimateSource : "예상 이동시간 미확인"; }
    private void heading(String title) { TextView t = text(title, wide() ? 34 : 29, TEXT, true); t.setPadding(0, dp(8), 0, dp(10)); body.addView(t); }
    private void paragraph(String copy) { TextView t = text(copy, 16, MUTED, false); t.setPadding(0, dp(4), 0, dp(16)); body.addView(t); }
    private TextView text(String content, int size, int color, boolean bold) {
        TextView t = new TextView(this); t.setText(content); t.setTextColor(color); t.setTextSize(wide() ? size * 1.4f : size); t.setLineSpacing(dp(4), 1);
        t.setFontFeatureSettings("kern"); if (bold) t.setTypeface(Typeface.create("sans-serif", Typeface.BOLD)); return t;
    }
    private Button button(String label, Runnable action, boolean primary) {
        Button b = new Button(this); b.setText(label); b.setTextSize(wide() ? 22 : 16); b.setAllCaps(false); b.setMinHeight(dp(wide() ? 80 : 52));
        b.setTextColor(primary ? BG : TEXT); b.setTypeface(Typeface.DEFAULT, Typeface.BOLD); b.setBackground(tint(primary ? TEAL : PANEL));
        b.setPadding(dp(12), dp(9), dp(12), dp(9)); b.setOnClickListener(v -> action.run());
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2); lp.topMargin = dp(7); lp.bottomMargin = dp(7); b.setLayoutParams(lp); return b;
    }
    private GradientDrawable tint(int color) { GradientDrawable d = new GradientDrawable(); d.setColor(color); d.setCornerRadius(dp(12)); d.setStroke(dp(1), color == TEAL ? TEAL : LINE); return d; }
    private LinearLayout card() { LinearLayout l = column(); l.setPadding(dp(18), dp(18), dp(18), dp(18)); l.setBackground(tint(PANEL)); LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1,-2); p.topMargin = dp(8); p.bottomMargin = dp(8); l.setLayoutParams(p); return l; }
    private LinearLayout column() { LinearLayout l = new LinearLayout(this); l.setOrientation(LinearLayout.VERTICAL); return l; }
    private LinearLayout row() { LinearLayout l = new LinearLayout(this); l.setOrientation(LinearLayout.HORIZONTAL); return l; }
    private boolean wide() { return getResources().getConfiguration().screenWidthDp >= 700; }
    private LinearLayout responsiveRow() { return wide() ? row() : column(); }
    private void addResponsive(LinearLayout parent, View child) {
        if (wide()) { LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, -2, 1); lp.setMargins(0, dp(6), dp(8), dp(8)); parent.addView(child, lp); }
        else parent.addView(child);
    }
    private LinearLayout.LayoutParams weight() { LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, -2, 1); p.setMargins(dp(2),0,dp(2),0); return p; }
    private int dp(float value) { return Math.round(value * getResources().getDisplayMetrics().density); }
    private void toast(String text) { Toast.makeText(this, text, Toast.LENGTH_LONG).show(); }
}
