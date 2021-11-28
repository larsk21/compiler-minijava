package edu.kit.compiler.transform;

import firm.Firm;

public class JFirmSingleton {

    private static boolean isLinuxInitialized = false;

    public static void initializeFirmLinux() {
        if(!isLinuxInitialized) {
            Firm.init("x86_64-linux-gnu", new String[]{"pic=1"});
            isLinuxInitialized = true;
        }
    }
}
