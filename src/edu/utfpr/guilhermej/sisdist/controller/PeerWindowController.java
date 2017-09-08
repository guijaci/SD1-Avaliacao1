package edu.utfpr.guilhermej.sisdist.controller;

import edu.utfpr.guilhermej.sisdist.model.Peer;
import javafx.application.Platform;
import javafx.scene.control.Button;
import javafx.scene.control.TextArea;
import javafx.scene.layout.GridPane;
import javafx.stage.WindowEvent;

public class PeerWindowController {
    public Button buttonSend;
    public GridPane gridPane;
    public TextArea textAreaInput;
    public TextArea textAreaOutput;

    private Peer peer;

    private boolean initialized = false;

    public PeerWindowController(){
    }

    public void buttonClicked(){
        peer.sendMulticastMessage(textAreaInput.getText());
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