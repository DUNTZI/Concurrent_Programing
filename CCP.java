/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Main.java to edit this template
 */
package ccp_asia_pacific_airport;

/**
 *
 * @author user
 */
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class CCP {
    public static void main(String[] args) throws Exception {
        ATC atc = new ATC(3);
        Thread atcThread = new Thread(atc, "Thread-ATC");
        atcThread.start();

        Random rand = new Random();
        List<Thread> planes = new ArrayList<>();

        for (int i = 1; i <= 4; i++) {
            Plane p = new Plane("Plane-" + i, rand.nextInt(30) + 20, false, atc);
            Thread t = new Thread(p, p.getName());
            planes.add(t);
            t.start();
            Thread.sleep(rand.nextInt(2000));
        }

        while (atc.getPlanesLandedCount() < 3) {
            Thread.sleep(500);
        }

        Plane emergencyPlane = new Plane("Plane-5", rand.nextInt(30) + 20, true, atc);
        Thread t5 = new Thread(emergencyPlane, "Thread-Plane-5");
        planes.add(t5);
        t5.start();

        Thread.sleep(rand.nextInt(2000));

        Plane normalPlane6 = new Plane("Plane-6", rand.nextInt(30) + 20, false, atc);
        Thread t6 = new Thread(normalPlane6, "Thread-Plane-6");
        planes.add(t6);
        t6.start();

        for (Thread t : planes) {
            t.join();
        }

        atc.shutdown();
        atcThread.join();

        atc.printSummaryReport();
    }
}

