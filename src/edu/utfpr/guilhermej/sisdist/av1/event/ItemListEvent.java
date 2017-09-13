package edu.utfpr.guilhermej.sisdist.av1.event;

import edu.utfpr.guilhermej.sisdist.av1.model.SaleItem;

public class ItemListEvent {
    private final SaleItem item;
    private final ItemListEventType type;

    public enum ItemListEventType{
        ADDED, REMOVED, MODIFIED;
    }

    public ItemListEvent(SaleItem item, ItemListEventType type) {
        this.item = item;
        this.type = type;
    }

    public SaleItem getItem() {
        return item;
    }

    public ItemListEventType getType() {
        return type;
    }
}
