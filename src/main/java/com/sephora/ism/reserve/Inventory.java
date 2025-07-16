package com.sephora.ism.reserve;

import java.math.BigDecimal;

public class Inventory {

    private BigDecimal onHand;
    private BigDecimal rohm;
    private BigDecimal lost;
    private BigDecimal oobAdjustment;
    private BigDecimal dotHardReserveAtsYes;
    private BigDecimal dotHardReserveAtsNo;
    private BigDecimal retHardReserveAtsYes;
    private BigDecimal retHardReserveAtsNo;
    private BigDecimal heldHardReserve;

    public BigDecimal getOnHand() {
        return onHand;
    }

    public void setOnHand(BigDecimal onHand) {
        this.onHand = onHand;
    }

    public BigDecimal getRohm() {
        return rohm;
    }

    public void setRohm(BigDecimal rohm) {
        this.rohm = rohm;
    }

    public BigDecimal getLost() {
        return lost;
    }

    public void setLost(BigDecimal lost) {
        this.lost = lost;
    }

    public BigDecimal getOobAdjustment() {
        return oobAdjustment;
    }

    public void setOobAdjustment(BigDecimal oobAdjustment) {
        this.oobAdjustment = oobAdjustment;
    }

    public BigDecimal getDotHardReserveAtsYes() {
        return dotHardReserveAtsYes;
    }

    public void setDotHardReserveAtsYes(BigDecimal dotHardReserveAtsYes) {
        this.dotHardReserveAtsYes = dotHardReserveAtsYes;
    }

    public BigDecimal getDotHardReserveAtsNo() {
        return dotHardReserveAtsNo;
    }

    public void setDotHardReserveAtsNo(BigDecimal dotHardReserveAtsNo) {
        this.dotHardReserveAtsNo = dotHardReserveAtsNo;
    }

    public BigDecimal getRetHardReserveAtsYes() {
        return retHardReserveAtsYes;
    }

    public void setRetHardReserveAtsYes(BigDecimal retHardReserveAtsYes) {
        this.retHardReserveAtsYes = retHardReserveAtsYes;
    }

    public BigDecimal getRetHardReserveAtsNo() {
        return retHardReserveAtsNo;
    }

    public void setRetHardReserveAtsNo(BigDecimal retHardReserveAtsNo) {
        this.retHardReserveAtsNo = retHardReserveAtsNo;
    }

    public BigDecimal getHeldHardReserve() {
        return heldHardReserve;
    }

    public void setHeldHardReserve(BigDecimal heldHardReserve) {
        this.heldHardReserve = heldHardReserve;
    }
}
