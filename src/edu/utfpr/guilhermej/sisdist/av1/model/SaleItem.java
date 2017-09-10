package edu.utfpr.guilhermej.sisdist.av1.model;

public class SaleItem implements Comparable<SaleItem> {
    private String description;
    private float price;

    public String getDescription() {
        return description;
    }

    public SaleItem setDescription(String description) {
        this.description = description;
        return this;
    }

    public float getPrice() {
        return price;
    }

    public SaleItem setPrice(float price) {
        this.price = price;
        return this;
    }
    @Override
    public boolean equals(Object obj) {
        if(!SaleItem.class.isInstance(obj))
            return false;
        SaleItem item = SaleItem.class.cast(obj);
        return getDescription().equals(description);
    }

    @Override
    public int compareTo(SaleItem o) {
        return getDescription().compareTo(o.getDescription());
    }
}
