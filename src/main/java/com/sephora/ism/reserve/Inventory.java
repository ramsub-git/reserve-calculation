package com.sephora.ism.reserve;

import java.math.BigDecimal;

public class Inventory {
    private BigDecimal ssohu;       // On Hand
    private BigDecimal ssrohm;      // Rohm
    private BigDecimal sslost;      // Lost
    private BigDecimal ssdmg;       // Damaged
    private BigDecimal ssrohp;      // Retail Pick Reserve
    private BigDecimal dotShipNotBill;
    private BigDecimal dotOpenCustOrder;
    private BigDecimal dotHardReserveAtsYes;
    private BigDecimal dotHardReserveAtsNo;
    private BigDecimal retHardReserveAtsYes;
    private BigDecimal retHardReserveAtsNo;
    private BigDecimal heldHardReserve;
    private BigDecimal dotReserve;
    private BigDecimal retReserve;
    private BigDecimal dotOutb;
    private BigDecimal retNeed;

    // Constructors, getters, and setters
    public Inventory() {}

    public Inventory(
            BigDecimal ssohu, BigDecimal ssrohm, BigDecimal sslost,
            BigDecimal ssdmg, BigDecimal ssrohp, BigDecimal dotShipNotBill,
            BigDecimal dotOpenCustOrder, BigDecimal dotHardReserveAtsYes,
            BigDecimal dotHardReserveAtsNo, BigDecimal retHardReserveAtsYes,
            BigDecimal retHardReserveAtsNo, BigDecimal heldHardReserve,
            BigDecimal dotReserve, BigDecimal retReserve,
            BigDecimal dotOutb, BigDecimal retNeed
    ) {
        this.ssohu = ssohu;
        this.ssrohm = ssrohm;
        this.sslost = sslost;
        this.ssdmg = ssdmg;
        this.ssrohp = ssrohp;
        this.dotShipNotBill = dotShipNotBill;
        this.dotOpenCustOrder = dotOpenCustOrder;

        // New fields
        this.dotHardReserveAtsYes = dotHardReserveAtsYes;
        this.dotHardReserveAtsNo = dotHardReserveAtsNo;
        this.retHardReserveAtsYes = retHardReserveAtsYes;
        this.retHardReserveAtsNo = retHardReserveAtsNo;
        this.heldHardReserve = heldHardReserve;
        this.dotReserve = dotReserve;
        this.retReserve = retReserve;
        this.dotOutb = dotOutb;
        this.retNeed = retNeed;
    }


    // Getters
    public BigDecimal getSsohu() { return ssohu; }
    public BigDecimal getSsrohm() { return ssrohm; }
    public BigDecimal getSslost() { return sslost; }
    public BigDecimal getSsdmg() { return ssdmg; }
    public BigDecimal getSsrohp() { return ssrohp; }
    public BigDecimal getDotShipNotBill() { return dotShipNotBill; }
    public BigDecimal getDotOpenCustOrder() { return dotOpenCustOrder; }
}