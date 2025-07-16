// Inventory.java - Updated to include OOB and remove damaged
package com.sephora.ism.reserve;

import java.math.BigDecimal;

public class Inventory {
    private BigDecimal ssohu;       // On Hand
    private BigDecimal ssrohm;      // Rohm
    private BigDecimal sslost;      // Lost
    private BigDecimal oobAdjustment; // Out of Balance adjustment
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

    // Constructors
    public Inventory() {
    }

    // Getters and setters
    public BigDecimal getSsohu() {
        return ssohu != null ? ssohu : BigDecimal.ZERO;
    }

    public void setSsohu(BigDecimal ssohu) {
        this.ssohu = ssohu;
    }

    public BigDecimal getSsrohm() {
        return ssrohm != null ? ssrohm : BigDecimal.ZERO;
    }

    public void setSsrohm(BigDecimal ssrohm) {
        this.ssrohm = ssrohm;
    }

    public BigDecimal getSslost() {
        return sslost != null ? sslost : BigDecimal.ZERO;
    }

    public void setSslost(BigDecimal sslost) {
        this.sslost = sslost;
    }

    public BigDecimal getOobAdjustment() {
        return oobAdjustment != null ? oobAdjustment : BigDecimal.ZERO;
    }

    public void setOobAdjustment(BigDecimal oobAdjustment) {
        this.oobAdjustment = oobAdjustment;
    }

    public BigDecimal getSsrohp() {
        return ssrohp != null ? ssrohp : BigDecimal.ZERO;
    }

    public void setSsrohp(BigDecimal ssrohp) {
        this.ssrohp = ssrohp;
    }

    public BigDecimal getDotShipNotBill() {
        return dotShipNotBill != null ? dotShipNotBill : BigDecimal.ZERO;
    }

    public void setDotShipNotBill(BigDecimal dotShipNotBill) {
        this.dotShipNotBill = dotShipNotBill;
    }

    public BigDecimal getDotOpenCustOrder() {
        return dotOpenCustOrder != null ? dotOpenCustOrder : BigDecimal.ZERO;
    }

    public void setDotOpenCustOrder(BigDecimal dotOpenCustOrder) {
        this.dotOpenCustOrder = dotOpenCustOrder;
    }

    public BigDecimal getDotHardReserveAtsYes() {
        return dotHardReserveAtsYes != null ? dotHardReserveAtsYes : BigDecimal.ZERO;
    }

    public void setDotHardReserveAtsYes(BigDecimal dotHardReserveAtsYes) {
        this.dotHardReserveAtsYes = dotHardReserveAtsYes;
    }

    public BigDecimal getDotHardReserveAtsNo() {
        return dotHardReserveAtsNo != null ? dotHardReserveAtsNo : BigDecimal.ZERO;
    }

    public void setDotHardReserveAtsNo(BigDecimal dotHardReserveAtsNo) {
        this.dotHardReserveAtsNo = dotHardReserveAtsNo;
    }

    public BigDecimal getRetHardReserveAtsYes() {
        return retHardReserveAtsYes != null ? retHardReserveAtsYes : BigDecimal.ZERO;
    }

    public void setRetHardReserveAtsYes(BigDecimal retHardReserveAtsYes) {
        this.retHardReserveAtsYes = retHardReserveAtsYes;
    }

    public BigDecimal getRetHardReserveAtsNo() {
        return retHardReserveAtsNo != null ? retHardReserveAtsNo : BigDecimal.ZERO;
    }

    public void setRetHardReserveAtsNo(BigDecimal retHardReserveAtsNo) {
        this.retHardReserveAtsNo = retHardReserveAtsNo;
    }

    public BigDecimal getHeldHardReserve() {
        return heldHardReserve != null ? heldHardReserve : BigDecimal.ZERO;
    }

    public void setHeldHardReserve(BigDecimal heldHardReserve) {
        this.heldHardReserve = heldHardReserve;
    }

    public BigDecimal getDotReserve() {
        return dotReserve != null ? dotReserve : BigDecimal.ZERO;
    }

    public void setDotReserve(BigDecimal dotReserve) {
        this.dotReserve = dotReserve;
    }

    public BigDecimal getRetReserve() {
        return retReserve != null ? retReserve : BigDecimal.ZERO;
    }

    public void setRetReserve(BigDecimal retReserve) {
        this.retReserve = retReserve;
    }

    public BigDecimal getDotOutb() {
        return dotOutb != null ? dotOutb : BigDecimal.ZERO;
    }

    public void setDotOutb(BigDecimal dotOutb) {
        this.dotOutb = dotOutb;
    }

    public BigDecimal getRetNeed() {
        return retNeed != null ? retNeed : BigDecimal.ZERO;
    }

    public void setRetNeed(BigDecimal retNeed) {
        this.retNeed = retNeed;
    }
}