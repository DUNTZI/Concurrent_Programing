/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package ccp_asia_pacific_airport;

/**
 *
 * @author user
 */
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.logging.Level;
import java.util.logging.Logger;

public class ATC implements Runnable {

    private final int totalGates;
    private final List<Plane> landingQueue = new ArrayList<>();
    private final String[] gates;

    private final Lock queueLock = new ReentrantLock();
    private final Lock runwayLock = new ReentrantLock();
    private final Semaphore refuelTruck = new Semaphore(1, true);

    private final AtomicInteger planesOnGround = new AtomicInteger(0);
    private final AtomicInteger planesLanded = new AtomicInteger(0);
    private final AtomicInteger planesDeparted = new AtomicInteger(0);
    private final AtomicInteger emergencyHandled = new AtomicInteger(0);
    private final AtomicInteger totalPassengers = new AtomicInteger(0);

    private final List<Long> waitingTimes = new ArrayList<>();
    private volatile boolean running = true;
    private int lastQueueSize = -1;

    public ATC(int gateCount) {
        this.totalGates = gateCount;
        this.gates = new String[gateCount];
        for (int i = 0; i < gateCount; i++) {
            gates[i] = null;
        }
    }
    @Override
    public void run() {
        log("ATC: Air Traffic Control is online...");
        while (running) {
            queueLock.lock();
            try {
                Plane next = getNextPlane();
                int freeGate = freeGateId();

                if (next != null && freeGate != -1 && planesOnGround.get() < totalGates) {
                    landingQueue.remove(next);
                    int gateId = reserveGate(next.getName());
                    planesOnGround.incrementAndGet();

                    log("ATC: Gate-" + gateId + " prepared for " + next.getName() + " landing.");
                    next.grantLanding(gateId);
                    lastQueueSize = landingQueue.size();
                } else {
                    if (landingQueue.size() != lastQueueSize) {
                        log("ATC: " + landingQueue.size() + " plane(s) waiting.");
                        lastQueueSize = landingQueue.size();
                    }
                }
            } finally {
                queueLock.unlock();
            }

            try {
                Thread.sleep(200);
            } catch (InterruptedException ignored) {}
        }
    }

    public void shutdown() {
        running = false;
    }

    public void requestLanding(Plane plane) throws InterruptedException {
        long requestStart = System.currentTimeMillis();

        queueLock.lock();
        try {
            if (!landingQueue.contains(plane) && !plane.isLandingGranted()) {
                landingQueue.add(plane);
                if (plane.isEmergency()) {
                    log("ATC: " + plane.getName() + " [EMERGENCY] added to queue with priority!");
                } else {
                    log("ATC: " + plane.getName() + " requesting landing (added to queue).");
                }
            } else {
                log("ATC: " + plane.getName() + " has already requested landing or has landing granted; ignoring duplicate request.");
            }
        } finally {
            queueLock.unlock();
        }

        plane.waitForLandingClearance();
        long waited = System.currentTimeMillis() - requestStart;

        synchronized (waitingTimes) {
            waitingTimes.add(waited);
        }
    }

    public void useRunwayForLanding(String planeName, boolean isEmergency) throws InterruptedException {
        runwayLock.lock();
        try {
            log("ATC: " + planeName + " is cleared for landing.");
            Thread.sleep(2000);
            log("ATC: " + planeName + " has landed successfully and vacated the runway.");
            planesLanded.incrementAndGet();
            if (isEmergency) emergencyHandled.incrementAndGet();
        } finally {
            runwayLock.unlock();
        }
    }

    public void useRunwayForTakeoff(String planeName) throws InterruptedException {
        runwayLock.lock();
        try {
            log("ATC: " + planeName + " cleared for takeoff.");
            Thread.sleep(1500);
            log("ATC: " + planeName + " has taken off and runway is now clear.");
            planesDeparted.incrementAndGet();
        } finally {
            runwayLock.unlock();
        }
    }

    public void runwayCleared(String planeName) {
        log("ATC: Runway is now clear after " + planeName + " landed and is ready for the next plane.");
    }

    public void requestRefuel(String planeName) throws InterruptedException {
        log("ATC: " + planeName + " requesting refuelling truck...");
        refuelTruck.acquire();
        log("ATC: Refuelling truck assigned to " + planeName + ". Refuelling in progress...");
    }

    public void releaseRefuel(String planeName) {
        log("ATC: Refuelling completed for " + planeName + ". Refuelling truck released.");
        refuelTruck.release();
    }

    public void planeLeft(Plane plane, int gateId, int passengers) {
        queueLock.lock();
        try {
            gates[gateId - 1] = null;
            planesOnGround.decrementAndGet();
            totalPassengers.addAndGet(passengers);
            log("ATC: " + plane.getName() + " departed. Gate-" + gateId + " now available.");
        } finally {
            queueLock.unlock();
        }
    }

    private int freeGateId() {
        for (int i = 0; i < totalGates; i++) {
            if (gates[i] == null) return i + 1;
        }
        return -1;
    }

    private int reserveGate(String planeName) {
        for (int i = 0; i < totalGates; i++) {
            if (gates[i] == null) {
                gates[i] = planeName;
                return i + 1;
            }
        }
        return -1;
    }

    private Plane getNextPlane() {
        if (landingQueue.isEmpty()) return null;

        Plane priority = null;
        long earliest = Long.MAX_VALUE;
        for (Plane p : landingQueue) {
            if (p.isEmergency()) {
                return p; // emergency plane always first
            }
            if (p.getArrivalTime() < earliest) {
                earliest = p.getArrivalTime();
                priority = p;
            }
        }
        return priority;
    }

    public int getPlanesLandedCount() {
        return planesLanded.get();
    }

    public void printSummaryReport() {
        long min = Long.MAX_VALUE, max = 0, sum = 0;
        int count = waitingTimes.size();

        for (long t : waitingTimes) {
            if (t < min) min = t;
            if (t > max) max = t;
            sum += t;
        }

        double avg = (count == 0) ? 0 : (double) sum / count;

        System.out.println("\n================= FINAL ATC SUMMARY REPORT =================");
        System.out.println("Total Gates Available     : " + totalGates);
        System.out.println("Total Planes Landed       : " + planesLanded.get());
        System.out.println("Total Planes Departed     : " + planesDeparted.get());
        System.out.println("Emergency Planes Handled  : " + emergencyHandled.get());
        System.out.println("Passengers Boarded        : " + totalPassengers.get());
        System.out.println("-------------------------------------------------------------");

        for (int i = 0; i < totalGates; i++) {
            String status = (gates[i] == null) ? "Available" : "Occupied by " + gates[i];
            System.out.println("Gate " + (i + 1) + " Final Status: " + status);
        }

        System.out.printf("Waiting time (ms): Min=%d, Avg=%.2f, Max=%d%n",
                (count == 0 ? 0 : min), avg, (count == 0 ? 0 : max));
        System.out.println("=============================================================");
        System.out.println("Simulation complete");
    }

    private void log(String msg) {
        System.out.println(msg);
    }
}
