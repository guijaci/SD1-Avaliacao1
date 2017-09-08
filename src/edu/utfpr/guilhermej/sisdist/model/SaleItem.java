package edu.utfpr.guilhermej.sisdist.model;

public class SaleItem {
    private String itemName;
    private int price;
    private int quantity;

    public String getItemName() {
        return itemName;
    }

    public SaleItem setItemName(String itemName) {
        this.itemName = itemName;
        return this;
    }

    public int getPrice() {
        return price;
    }

    public SaleItem setPrice(int price) {
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
}
