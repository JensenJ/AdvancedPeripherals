package de.srendi.advancedperipherals.common.addons.curios;

import de.srendi.advancedperipherals.common.addons.computercraft.peripheral.InventoryManagerPeripheral;

public class Integration implements Runnable {

    @Override
    public void run() {
        InventoryManagerPeripheral.addIntegrationPlugin(InventoryManagerCurioPlugin::new);
    }
}
