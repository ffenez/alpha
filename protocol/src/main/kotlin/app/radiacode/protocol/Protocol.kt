/*
 * Kotlin port of cdump/radiacode (https://github.com/cdump/radiacode).
 * Original work: MIT License, Copyright 2021 Maxim Andreev.
 */
package app.radiacode.protocol

/** Device command codes (cdump `COMMAND`). */
object Command {
    const val GET_STATUS = 0x0005
    const val SET_EXCHANGE = 0x0007
    const val GET_VERSION = 0x000A
    const val GET_SERIAL = 0x000B
    const val FW_IMAGE_GET_INFO = 0x0012
    const val FW_SIGNATURE = 0x0101
    const val RD_HW_CONFIG = 0x0807
    const val RD_FLASH = 0x081C
    const val RD_VIRT_SFR = 0x0824
    const val WR_VIRT_SFR = 0x0825
    const val RD_VIRT_STRING = 0x0826
    const val WR_VIRT_STRING = 0x0827
    const val RD_VIRT_SFR_BATCH = 0x082A
    const val WR_VIRT_SFR_BATCH = 0x082B
    const val SET_TIME = 0x0A04

    /** SET_EXCHANGE init payload sent once after connect. */
    val SET_EXCHANGE_PAYLOAD = byteArrayOf(0x01, 0xFF.toByte(), 0x12, 0xFF.toByte())
}

/** Virtual string (VS) identifiers (cdump `VS`). */
object Vs {
    const val CONFIGURATION = 2
    const val FW_DESCRIPTOR = 3
    const val SERIAL_NUMBER = 8
    const val TEXT_MESSAGE = 0x0F
    const val MEM_SNAPSHOT = 0xE0
    const val DATA_BUF = 0x100
    const val SFR_FILE = 0x101
    const val SPECTRUM = 0x200
    const val ENERGY_CALIB = 0x202

    /** Canonical accumulated-spectrum id (0x201 seen elsewhere is wrong). */
    const val SPEC_ACCUM = 0x205
    const val SPEC_DIFF = 0x206
    const val SPEC_RESET = 0x207
}

/** Virtual special function register (VSFR) identifiers (cdump `VSFR`). */
object Vsfr {
    const val DEVICE_CTRL = 0x0500L
    const val DEVICE_LANG = 0x0502L
    const val DEVICE_ON = 0x0503L
    const val DEVICE_TIME = 0x0504L

    const val DISP_CTRL = 0x0510L
    const val DISP_BRT = 0x0511L
    const val DISP_CONTR = 0x0512L
    const val DISP_OFF_TIME = 0x0513L
    const val DISP_ON = 0x0514L
    const val DISP_DIR = 0x0515L
    const val DISP_BACKLT_ON = 0x0516L

    const val SOUND_CTRL = 0x0520L
    const val SOUND_VOL = 0x0521L
    const val SOUND_ON = 0x0522L
    const val SOUND_BUTTON = 0x0523L

    const val VIBRO_CTRL = 0x0530L
    const val VIBRO_ON = 0x0531L

    const val LEDS_CTRL = 0x0540L
    const val LED0_BRT = 0x0541L
    const val LED1_BRT = 0x0542L
    const val LED2_BRT = 0x0543L
    const val LED3_BRT = 0x0544L
    const val LEDS_ON = 0x0545L

    const val ALARM_MODE = 0x05E0L
    const val PLAY_SIGNAL = 0x05E1L

    const val MS_CTRL = 0x0600L
    const val MS_MODE = 0x0601L
    const val MS_SUB_MODE = 0x0602L
    const val MS_RUN = 0x0603L

    const val BLE_TX_PWR = 0x0700L

    const val DR_LEV1_uR_h = 0x8000L
    const val DR_LEV2_uR_h = 0x8001L
    const val DS_LEV1_100uR = 0x8002L
    const val DS_LEV2_100uR = 0x8003L
    const val DS_UNITS = 0x8004L
    const val CPS_FILTER = 0x8005L
    const val RAW_FILTER = 0x8006L
    const val DOSE_RESET = 0x8007L
    const val CR_LEV1_cp10s = 0x8008L
    const val CR_LEV2_cp10s = 0x8009L

    const val USE_nSv_h = 0x800CL

    const val CHN_TO_keV_A0 = 0x8010L
    const val CHN_TO_keV_A1 = 0x8011L
    const val CHN_TO_keV_A2 = 0x8012L
    const val CR_UNITS = 0x8013L
    const val DS_LEV1_uR = 0x8014L
    const val DS_LEV2_uR = 0x8015L

    const val CPS = 0x8020L
    const val DR_uR_h = 0x8021L
    const val DS_uR = 0x8022L

    const val TEMP_degC = 0x8024L
    const val ACC_X = 0x8025L
    const val ACC_Y = 0x8026L
    const val ACC_Z = 0x8027L
    const val OPT = 0x8028L

    const val RAW_TEMP_degC = 0x8033L
    const val TEMP_UP_degC = 0x8034L
    const val TEMP_DN_degC = 0x8035L

    const val VBIAS_mV = 0xC000L
    const val COMP_LEV = 0xC001L
    const val CALIB_MODE = 0xC002L
    const val DPOT_RDAC = 0xC004L
    const val DPOT_RDAC_EEPROM = 0xC005L
    const val DPOT_TOLER = 0xC006L

    const val SYS_MCU_ID0 = 0xFFFF0000L
    const val SYS_MCU_ID1 = 0xFFFF0001L
    const val SYS_MCU_ID2 = 0xFFFF0002L

    const val SYS_DEVICE_ID = 0xFFFF0005L
    const val SYS_SIGNATURE = 0xFFFF0006L
    const val SYS_RX_SIZE = 0xFFFF0007L
    const val SYS_TX_SIZE = 0xFFFF0008L
    const val SYS_BOOT_VERSION = 0xFFFF0009L
    const val SYS_TARGET_VERSION = 0xFFFF000AL
    const val SYS_STATUS = 0xFFFF000BL
    const val SYS_MCU_VREF = 0xFFFF000CL
    const val SYS_MCU_TEMP = 0xFFFF000DL
}
