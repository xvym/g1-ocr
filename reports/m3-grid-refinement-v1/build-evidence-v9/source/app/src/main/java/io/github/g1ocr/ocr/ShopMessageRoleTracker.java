package io.github.g1ocr.ocr;

/** A menu overlay does not replace an unchanged, still-visible merchant message. */
final class ShopMessageRoleTracker {
    private String signature="",role="";
    void reset(){signature="";role="";}
    String resolve(MenuFieldReader.Layout layout,String current,String fallback) {
        boolean shop=layout==MenuFieldReader.Layout.SHOP_PARENT||layout==MenuFieldReader.Layout.SHOP_BUY||layout==MenuFieldReader.Layout.SHOP_SELL_LIST;
        if(!shop||current.isEmpty()){reset();return fallback;}
        String resolved=current.equals(signature)&&!role.isEmpty()?role:fallback;
        signature=current;role=resolved;return resolved;
    }
}
