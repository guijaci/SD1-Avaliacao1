package edu.utfpr.guilhermej.sisdist.av1.model;

public class SaleItem implements Comparable<SaleItem> {
    private String itemName;
    private float price;
    private int quantity;

    public String getItemName() {
        return itemName;
    }

    public SaleItem setItemName(String itemName) {
        this.itemName = itemName;
        return this;
    }

    public float getPrice() {
        return price;
    }

    public SaleItem setPrice(float price) {
        this.price = price;
        return this;
    }

    public int getQuantity() {
        return quantity;
    }

    public SaleItem setQuantity(int quantity) {
        this.quantity = quantity;
        return this;
    }

    @Override
    public boolean equals(Object obj) {
        if(!SaleItem.class.isInstance(obj))
            return false;
        SaleItem item = SaleItem.class.cast(obj);
        return getItemName().equals(itemName);
    }

    @Override
    public int compareTo(SaleItem o) {
        return getItemName().compareTo(o.getItemName());
    }
}
