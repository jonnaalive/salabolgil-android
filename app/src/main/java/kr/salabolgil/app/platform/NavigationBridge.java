package kr.salabolgil.app.platform;

import android.app.Activity;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;

import ai.pleos.playground.navi.constants.NaviErrorCode;
import ai.pleos.playground.navi.constants.RouteOption;
import ai.pleos.playground.navi.data.DestinationArrivedInfo;
import ai.pleos.playground.navi.data.RouteInfo;
import ai.pleos.playground.navi.data.RouteStartInfo;
import ai.pleos.playground.navi.helper.NaviHelper;
import ai.pleos.playground.navi.helper.listener.NaviHelperEventListener;
import kotlin.Unit;
import java.util.UUID;

/**
 * Real Pleos NaviHelper adapter. It deliberately contains no simulated route or arrival events.
 * Connection, route request, navigation arrival and user-confirmed facility use are separate facts.
 * All public calls and application callbacks are made on the main thread.
 */
public final class NavigationBridge {
    public interface Callback {
        void onReady();
        void onFailure(String message);
        void onArrival();
        void onDisconnected();
        void onUxRestrictionChanged(boolean restricted);
        /** An SDK onRouteStarted event matched the pending destination. Not request dispatch. */
        default void onRouteStarted() {}
    }

    private final Activity activity;
    private final Callback callback;
    private final Handler main = new Handler(Looper.getMainLooper());
    private NaviHelper helper;
    private CarMonitor monitor;
    private boolean connected;
    private boolean connecting;
    private boolean closed;
    private String detail = "Pleos 연결 전";
    private long generation;
    private long requestGeneration;
    private boolean cancelPending;
    private boolean awaitingRoute;
    private boolean routeStarted;
    private boolean arrivalDelivered;
    private String requestedName;
    private Runnable requestTimeout;
    private Runnable cancelTimeout;
    private Runnable connectTimeout;
    private NaviHelperEventListener listener;

    public NavigationBridge(Activity activity, Callback callback) {
        this.activity = activity;
        this.callback = callback;
    }

    private NaviHelperEventListener createListener(long token) {
        return new NaviHelperEventListener() {
        @Override public void onRouteStarted(RouteStartInfo info) {
            long requestToken = requestGeneration;
            main.post(() -> {
                if (closed || generation != token || requestGeneration != requestToken
                        || cancelPending || !awaitingRoute || info == null
                        || !sameName(info.getName(), requestedName)) return;
                awaitingRoute = false;
                routeStarted = true;
                clearRequestTimeout();
                detail = "Pleos 경로 탐색 시작 이벤트 수신";
                callback.onRouteStarted();
            });
        }

        @Override public void onDestinationArrived(DestinationArrivedInfo info) {
            long requestToken = requestGeneration;
            main.post(() -> {
                // A global navigation event must not complete an unrelated or previous task.
                if (closed || generation != token || requestGeneration != requestToken
                        || cancelPending || !routeStarted || arrivalDelivered || info == null
                        || !sameName(info.getName(), requestedName)) return;
                arrivalDelivered = true;
                routeStarted = false;
                detail = "Pleos 도착 알림 수신 · 이용 여부는 별도 확인";
                callback.onArrival();
            });
        }

        @Override public void onRouteCancelled() {
            long requestToken = requestGeneration;
            main.post(() -> {
                if (closed || generation != token || requestGeneration != requestToken) return;
                if (cancelPending) {
                    clearCancellation();
                    detail = "내비 경로 취소 확인됨 · 다음 목적지 요청 가능";
                    return;
                }
                // This SDK event carries no request ID/name. Outside our cancellation barrier,
                // an old duplicate or another navigation client's cancel cannot safely be
                // attributed to the current trial. Never fail a new request on this event alone.
                if (awaitingRoute || routeStarted) {
                    detail = "내비 취소 알림 수신 · 현재 목적지 상태는 별도 확인 필요";
                }
            });
        }

        @Override public void onError(NaviErrorCode code) {
            long requestToken = requestGeneration;
            main.post(() -> {
                if (closed || generation != token || requestGeneration != requestToken
                        || cancelPending || (!awaitingRoute && !routeStarted)) return;
                resetRequest();
                detail = "Pleos 경로 요청 오류: " + (code == null ? "알 수 없음" : code.name());
                callback.onFailure(detail);
            });
        }
        };
    }

    public void connect() {
        if (closed || connected || connecting) return;
        if (!activity.getPackageManager().hasSystemFeature(PackageManager.FEATURE_AUTOMOTIVE)) {
            fail("차량용 Android 환경이 아닙니다. Pleos 내비 연결은 사용할 수 없습니다.");
            return;
        }
        if (Build.VERSION.SDK_INT < 29) {
            fail("이 빌드의 차량 UX 상태 감지는 Android 10 이상에서 지원합니다.");
            return;
        }
        connecting = true;
        detail = "Pleos 내비와 차량 UX 상태 연결 중";
        long token = ++generation;
        try {
            releaseHelper();
            clearCancellation();
            if (monitor != null) monitor.close();
            monitor = new CarMonitor();
            monitor.connect();
            listener = createListener(token);
            helper = new NaviHelper(activity.getApplicationContext());
            connectTimeout = () -> {
                if (closed || generation != token || !connecting) return;
                invalidateNavigation("Pleos 초기 연결 응답을 받지 못했습니다. 다시 연결해 주세요.");
            };
            main.postDelayed(connectTimeout, 20_000);
            helper.initialize(() -> {
                main.post(() -> {
                    if (closed || generation != token) return;
                    clearConnectTimeout();
                    connecting = false;
                    try {
                        helper.addListener(listener);
                        connected = true;
                        detail = "Pleos 내비 서비스 연결됨 · 경로는 아직 요청하지 않음";
                        callback.onReady();
                    } catch (RuntimeException | LinkageError error) {
                        connected = false;
                        fail("Pleos 이벤트 연결 실패: " + error.getClass().getSimpleName());
                    }
                });
                return Unit.INSTANCE;
            }, throwable -> {
                main.post(() -> {
                    if (closed || generation != token) return;
                    invalidateNavigation("Pleos 내비 서비스에 연결하지 못했습니다. 연결 재시도가 필요합니다.");
                });
                return Unit.INSTANCE;
            });
        } catch (RuntimeException | LinkageError error) {
            invalidateNavigation("Pleos 연결 실패: " + error.getClass().getSimpleName());
        }
    }

    public boolean isConnected() { return !closed && connected; }

    public void navigate(String name, double latitude, double longitude) {
        if (closed) return;
        if (cancelPending) {
            fail("이전 경로의 취소 확인을 기다리고 있습니다. 잠시 후 다시 시도해 주세요.");
            return;
        }
        if (!connected || helper == null) {
            fail("Pleos 내비 서비스 연결을 먼저 확인해 주세요.");
            return;
        }
        if (!hasVerifiedUxState() || isRestricted()) {
            fail("차량 UX 상태가 제한 중이거나 미확인입니다. 주차 후 다시 확인해 주세요.");
            return;
        }
        if (name == null || name.trim().isEmpty() || !Double.isFinite(latitude)
                || !Double.isFinite(longitude) || latitude < -90 || latitude > 90
                || longitude < -180 || longitude > 180) {
            fail("목적지 이름과 올바른 위도·경도를 입력해 주세요.");
            return;
        }
        if (awaitingRoute || routeStarted) {
            fail("진행 중인 경로가 있습니다. 현재 체험을 마치거나 중단해 주세요.");
            return;
        }
        // SDK events contain no application request ID. A unique navigation-only label prevents
        // late events for an identically named facility in another candidate/session matching.
        requestedName = name.trim() + " · 생활체험 " + UUID.randomUUID();
        long requestToken = ++requestGeneration;
        long token = generation;
        awaitingRoute = true;
        arrivalDelivered = false;
        detail = "Pleos에 경로 요청 중 · 결과 대기";
        // Verified against navi-data 3.0.4, not the outdated nine-argument Java web example.
        // Its actual constructor takes latitude FIRST, longitude SECOND, and eleven arguments.
        RouteInfo route = new RouteInfo(latitude, longitude, null, null,
                requestedName, "", "", "", RouteOption.RECOMMENDED, "", false);
        requestTimeout = () -> {
            if (closed || generation != token || requestGeneration != requestToken
                    || !awaitingRoute) return;
            invalidateNavigation("경로 탐색 응답을 받지 못했습니다. 다시 연결한 뒤 요청해 주세요.");
        };
        main.postDelayed(requestTimeout, 30_000);
        try {
            helper.requestRoute(route);
        } catch (RuntimeException | LinkageError error) {
            resetRequest();
            fail("Pleos 경로 요청 실패: " + error.getClass().getSimpleName());
        }
    }

    public boolean hasVerifiedUxState() {
        return !closed && connected && monitor != null && monitor.refresh();
    }

    public boolean isRestricted() {
        return !hasVerifiedUxState() || monitor.restricted;
    }

    public String description() {
        if (closed) return "연결 종료";
        return detail + (monitor == null ? " · 차량 UX 미확인"
                : monitor.known ? (monitor.restricted ? " · UX 제한 중" : " · UX 제한 없음(주차 증거 아님)")
                : " · 차량 UX 미확인");
    }

    /** Called only for an explicit user stop/skip/manual completion before the next request. */
    public void cancel() {
        if (closed || cancelPending) return;
        boolean hadRequest = awaitingRoute || routeStarted;
        resetRequest();
        if (!closed && connected && helper != null && hadRequest) {
            cancelPending = true;
            long token = generation;
            cancelTimeout = () -> {
                if (closed || generation != token || !cancelPending) return;
                invalidateNavigation("경로 취소 응답을 받지 못했습니다. 다시 연결한 뒤 다음 목적지를 요청해 주세요.");
            };
            main.postDelayed(cancelTimeout, 10_000);
            try {
                helper.cancelRoute();
                detail = "내비 경로 취소 요청 전송 · 확인 대기";
            } catch (RuntimeException | LinkageError error) {
                invalidateNavigation("내비 경로 취소 요청 실패: " + error.getClass().getSimpleName());
            }
        }
    }

    private void fail(String message) {
        detail = message;
        long token = generation;
        main.post(() -> { if (!closed && generation == token) callback.onFailure(message); });
    }

    private void clearCancellation() {
        if (cancelTimeout != null) main.removeCallbacks(cancelTimeout);
        cancelTimeout = null;
        cancelPending = false;
    }

    private void clearConnectTimeout() {
        if (connectTimeout != null) main.removeCallbacks(connectTimeout);
        connectTimeout = null;
    }

    private void releaseHelper() {
        if (helper == null) return;
        if (listener != null) {
            try { helper.removeListener(listener); } catch (RuntimeException | LinkageError ignored) {}
        }
        try { helper.release(); } catch (RuntimeException | LinkageError ignored) {}
        helper = null;
        listener = null;
    }

    private void invalidateNavigation(String message) {
        ++generation;
        connected = false;
        connecting = false;
        resetRequest();
        clearCancellation();
        clearConnectTimeout();
        releaseHelper();
        callback.onDisconnected();
        fail(message);
    }

    private static boolean sameName(String received, String requested) {
        return received != null && requested != null && received.trim().equals(requested);
    }

    private void clearRequestTimeout() {
        if (requestTimeout != null) main.removeCallbacks(requestTimeout);
        requestTimeout = null;
    }

    private void resetRequest() {
        ++requestGeneration;
        clearRequestTimeout();
        awaitingRoute = false;
        routeStarted = false;
        arrivalDelivered = false;
        requestedName = null;
    }

    public void close() {
        closed = true;
        ++generation;
        connected = false;
        connecting = false;
        resetRequest();
        clearCancellation();
        clearConnectTimeout();
        main.removeCallbacksAndMessages(null);
        releaseHelper();
        if (monitor != null) monitor.close();
        monitor = null;
    }

    /** Loaded only on Automotive devices. No speed, gear or presumed parked state is read. */
    private final class CarMonitor {
        private android.car.Car car;
        private android.car.drivingstate.CarUxRestrictionsManager manager;
        private boolean known;
        private boolean restricted = true;
        private boolean stopped;

        void connect() {
            car = android.car.Car.createCar(activity.getApplicationContext(), main,
                    android.car.Car.CAR_WAIT_TIMEOUT_DO_NOT_WAIT, (service, ready) -> {
                        if (closed || stopped) return;
                        if (!ready) {
                            unknown();
                            callback.onDisconnected();
                            return;
                        }
                        car = service;
                        try {
                            manager = (android.car.drivingstate.CarUxRestrictionsManager)
                                    service.getCarManager(android.car.Car.CAR_UX_RESTRICTION_SERVICE);
                            if (manager == null) { unknown(); return; }
                            manager.registerListener(this::accept);
                            accept(manager.getCurrentCarUxRestrictions());
                        } catch (RuntimeException | LinkageError error) { unknown(); }
                    });
        }

        boolean refresh() {
            if (closed || stopped || car == null || manager == null) { known = false; restricted = true; return false; }
            try {
                if (!car.isConnected()) { unknown(); return false; }
                android.car.drivingstate.CarUxRestrictions current = manager.getCurrentCarUxRestrictions();
                if (current == null) { unknown(); return false; }
                accept(current);
                return known;
            } catch (RuntimeException | LinkageError error) { unknown(); return false; }
        }

        void accept(android.car.drivingstate.CarUxRestrictions value) {
            if (closed || stopped) return;
            if (value == null) { unknown(); return; }
            boolean next = value.isRequiresDistractionOptimization()
                    || value.getActiveRestrictions() != 0;
            boolean changed = !known || restricted != next;
            known = true;
            restricted = next;
            if (changed) main.post(() -> { if (!closed && !stopped) callback.onUxRestrictionChanged(next); });
        }

        void unknown() {
            boolean changed = known || !restricted;
            known = false;
            restricted = true;
            if (changed) main.post(() -> { if (!closed && !stopped) callback.onUxRestrictionChanged(true); });
        }

        void close() {
            stopped = true;
            known = false;
            restricted = true;
            if (manager != null) {
                try { manager.unregisterListener(); } catch (RuntimeException | LinkageError ignored) {}
            }
            if (car != null) {
                try { car.disconnect(); } catch (RuntimeException | LinkageError ignored) {}
            }
            manager = null;
            car = null;
        }
    }
}
