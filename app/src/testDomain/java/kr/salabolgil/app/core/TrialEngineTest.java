package kr.salabolgil.app.core;

import java.io.*;
import static kr.salabolgil.app.core.AppData.*;

/** Run with javac/java; exercises domain behavior without Android or mocked platform APIs. */
public final class TrialEngineTest {
    private static int assertions;
    public static void main(String[] args) throws Exception {
        testArrivalAndComparison(); testSafety(); testFailureAndNext(); testSkipAndPersistence(); testResume(); testProvenance(); testAutomotiveDemoRestriction();
        System.out.println("PASS: " + assertions + " domain assertions");
    }
    static void testArrivalAndComparison() {
        TrialEngine e = new TrialEngine(AppData.fresh(true));
        e.begin(0,1); check(e.active().stage == Stage.REQUESTED, "dispatch is not route start");
        e.routeStarted(); check(!e.canEdit(), "demo driving locks details");
        check(e.arrive(), "first arrival applies"); check(!e.arrive(), "duplicate arrival ignored");
        check(e.active().arrivalCount == 1, "one arrival transition");
        check(e.active().use == Use.UNKNOWN, "arrival does not infer facility use");
        check(!e.canEdit(), "arrival does not infer parked");
        throwsState(() -> e.saveRecord(18,Use.AVAILABLE,"가능","가능",""));
        e.confirmParked(); e.saveRecord(18,Use.AVAILABLE,"가능","가능","직접 확인");
        check(e.active().stage == Stage.RECORDED, "only record completes");
        check(TrialEngine.difference(e.active()).equals("+6분"), "expected12 actual18 difference6");
        check(e.hospitalFit(e.active()).equals("내 기준 15분 초과"), "personal condition comparison");
        check(TrialEngine.actualText(e.data.candidates[1].tasks[1]).equals("미확인"), "same hospital on B stays unknown");
        check(e.data.activeTask == 1, "saving does not advance task");
        e.begin(0,2); check(e.data.activeTask == 2, "next requires explicit start");
    }
    static void testSafety() {
        TrialEngine e = new TrialEngine(AppData.fresh(false));
        check(!e.canEdit(), "unknown real UX fails closed");
        throwsState(e::confirmParked);
        e.updateUx(true,false); check(!e.canEdit(), "UX release is not parking");
        e.confirmParked(); check(e.canEdit(), "verified unrestricted plus parking permits input");
        e.updateUx(true,true); check(!e.canEdit() && !e.isParkedConfirmed(), "restriction revokes parking confirmation");
        e.updateUx(true,false); check(!e.canEdit(), "unlock requires new parking confirmation");
        e.confirmParked(); e.updateUx(false,false); check(!e.canEdit(), "lost monitor fails closed even unrestricted flag");
        TrialEngine restored = new TrialEngine(e.data); check(!restored.canEdit(), "vehicle safety is not restored from saved data");
    }
    static void testFailureAndNext() {
        TrialEngine e = new TrialEngine(AppData.fresh(true)); e.begin(0,1); e.routeStarted(); e.confirmParked();
        throwsState(() -> e.begin(0,2));
        e.saveRecord(null,Use.UNKNOWN,"미확인","미확인","");
        check(TrialEngine.difference(e.active()).equals("차이 미확인"), "missing actual is not zero");
        e.requestFailed(); check(e.active().stage == Stage.RECORDED, "late failure does not erase confirmed record");
        TrialEngine failed = new TrialEngine(AppData.fresh(true)); failed.begin(0,0); failed.requestFailed();
        check(failed.active() == null && failed.data.candidates[0].tasks[0].stage == Stage.READY, "failed request is retryable");
        failed.begin(0,0); check(failed.active().stage == Stage.REQUESTED, "retry possible");
        failed.routeStarted(); failed.requestFailed();
        check(failed.active() == null && failed.data.candidates[0].tasks[0].stage == Stage.READY, "error during active route becomes retryable");
    }
    static void testSkipAndPersistence() throws Exception {
        TrialEngine e = new TrialEngine(AppData.fresh(true)); e.begin(0,1); e.routeStarted();
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        new ObjectOutputStream(bytes).writeObject(e.data);
        AppData restored = (AppData)new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray())).readObject();
        TrialEngine next = new TrialEngine(restored);
        check(next.active().stage == Stage.TRAVELLING && !next.canEdit(), "process restart resumes locked task");
        next.confirmParked(); next.skip();
        check(next.active().stage == Stage.SKIPPED && next.active().actualMinutes == null && next.active().use == Use.UNKNOWN, "skip stays unknown");
        check(next.data.candidates[1].tasks[1].stage == Stage.READY, "A operation does not modify B");
        check(AppData.empty(true).candidates[0].tasks[1].destination.isEmpty(), "delete yields empty data, no reseeded destinations");
    }
    static void testResume() {
        TrialEngine e = new TrialEngine(AppData.fresh(true)); e.begin(0,0); e.routeStarted(); e.confirmParked(); e.pause();
        check(e.data.paused, "pause persisted"); check(!e.arrive(), "paused arrival ignored");
        e.resume(); check(!e.data.paused && e.active().stage == Stage.REQUESTED, "resume requires fresh route acceptance");
        check(!e.canEdit(), "resumed travel immediately locks details");
    }
    static void testProvenance() {
        Task task = AppData.fresh(true).candidates[0].tasks[1]; check(task.hasEstimate(), "full estimate provenance accepted");
        task.estimateDate = ""; check(!task.hasEstimate(), "estimate without date is unavailable");
        task.latitude = Double.NaN; check(!task.hasCoordinates(), "NaN coordinate rejected");
    }
    static void testAutomotiveDemoRestriction() {
        TrialEngine e = new TrialEngine(AppData.fresh(true)); e.setVehicleEnvironment(true);
        check(!e.canEdit(), "AAOS demo with unknown actual UX is locked"); throwsState(e::confirmParked);
        e.updateUx(true,true); check(!e.canEdit(), "AAOS demo cannot bypass actual restriction"); throwsState(e::confirmParked);
        e.updateUx(true,false); check(e.canEdit(), "AAOS demo available when actual UX verified unrestricted");
        e.begin(0,1); e.routeStarted(); e.updateUx(true,true); throwsState(e::confirmParked);
        check(!e.canEdit(), "reproduced parking cannot override actual driving");
        e.updateUx(true,false); e.confirmParked(); check(e.canEdit(), "demo parking plus actual unrestricted allows edit");
    }
    static void check(boolean condition,String label) { assertions++; if (!condition) throw new AssertionError(label); }
    static void throwsState(Runnable action) { assertions++; try { action.run(); throw new AssertionError("unsafe transition permitted"); } catch (IllegalStateException expected) {} }
}
