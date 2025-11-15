/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package ccp_asia_pacific_airport;

/**
 *
 * @author user
 */
public class Plane implements Runnable {
    private final String name;
    private final int passengersCount;
    private final boolean emergency;
    private final ATC atc;
    private final long arrivalTime;
    private int assignedGate = -1;
    private boolean landingGranted = false;
    private final Object landingLock = new Object();

    public Plane(String name, int passengersCount, boolean emergency, ATC atc) {
        this.name = name;
        this.passengersCount = passengersCount;
        this.emergency = emergency;
        this.atc = atc;
        this.arrivalTime = System.currentTimeMillis();
    }

    public String getName() {
        return name;
    }

    public boolean isEmergency() {
        return emergency;
    }

    public long getArrivalTime() {
        return arrivalTime;
    }

    public boolean isLandingGranted() {
        synchronized (landingLock) {
            return landingGranted;
        }
    }

    public void waitForLandingClearance() throws InterruptedException {
        synchronized (landingLock) {
            while (!landingGranted) {
                landingLock.wait();
            }
        }
    }

    public void grantLanding(int gateId) {
        synchronized (landingLock) {
            this.assignedGate = gateId;
            this.landingGranted = true;
            landingLock.notify();
        }
    }

    @Override
    public void run() {
        try {
            log("Requesting landing...");
            atc.requestLanding(this);
            atc.useRunwayForLanding(name, emergency);
            log("Landed, taxiing to Gate-" + assignedGate + "...");
            Thread.sleep(1500);
            log("Docked at Gate-" + assignedGate + ".");
            atc.runwayCleared(name);

            log("Disembarking passengers (" + passengersCount + " passengers)...");
            Thread.sleep(passengersCount * 100);
            log("All passengers disembarked.");

            atc.requestRefuel(name);
            Thread.sleep(2000);
            atc.releaseRefuel(name);

            log("Boarding new passengers (" + passengersCount + " passengers)...");
            Thread.sleep(passengersCount * 120);
            log("Boarding complete.");

            log("Requesting takeoff clearance...");
            atc.useRunwayForTakeoff(name);
            log("Takeoff successful!");

            atc.planeLeft(this, assignedGate, passengersCount);
        } catch (InterruptedException e) {
            log("Interrupted during process.");
            Thread.currentThread().interrupt();
        }
    }

    private void log(String msg) {
        System.out.println(name + " : " + msg);
    }
}
