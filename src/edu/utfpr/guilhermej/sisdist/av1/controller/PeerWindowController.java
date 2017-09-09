package edu.utfpr.guilhermej.sisdist.av1.controller;

import edu.utfpr.guilhermej.sisdist.av1.model.Peer;
import edu.utfpr.guilhermej.sisdist.av1.model.SaleItem;
import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.scene.control.Button;
import javafx.scene.control.TextArea;
import javafx.scene.layout.GridPane;
import javafx.stage.WindowEvent;

public class PeerWindowController {
    public Button buttonSend;
    public Button newSaleItemButton;
    public GridPane gridPane;
    public TextArea textAreaInput;
    public TextArea textAreaOutput;

    int counter = 0;

    private Peer peer;

    private boolean initialized = false;

    public PeerWindowController(){
    }

    public void buttonClicked(ActionEvent actionEvent) {
        peer.sendMulticastMessage(textAreaInput.getText());
    }

    public void createSaleItem(ActionEvent actionEvent) {
        SaleItem item = new SaleItem()
                .setItemName(String.format("%s[%03d]",peer.getId(),counter++))
                .setPrice(1f)
                .setQuantity(1);
        peer.addSaleItem(item);
    }

    public void exitApplication(WindowEvent event){
        peer.disconnect();
        Platform.exit();
    }

    public void setPeer(Peer peer) {
        this.peer = peer;
        peer.addMulticastMessageListener(textAreaOutput::setText);
    }

}
