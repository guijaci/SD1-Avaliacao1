package edu.utfpr.guilhermej.sisdist.av1.controller;

import edu.utfpr.guilhermej.sisdist.av1.model.Peer;
import edu.utfpr.guilhermej.sisdist.av1.model.SaleItem;
import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.GridPane;
import javafx.stage.WindowEvent;
import javafx.util.Pair;
import javafx.util.StringConverter;

import java.util.Optional;
import java.util.function.UnaryOperator;

public class PeerWindowController {
    public Button newSaleItemButton;
    public GridPane gridPane;
    public TextArea textAreaOutput;
    public Button searchItemButton;

    private Dialog<Pair<String, Float>> newSaleItemDialog = null;
    private TextInputDialog searchItemDialog = null;

    private final Object messageLock;

    private Peer peer;

    public PeerWindowController(){
        messageLock = new Object();
    }

    public void initialize(){
        newSaleItemDialog = buildNewSaleItemDialog();
        searchItemDialog = buildSearchItemDialog();
    }

    public void onCreateSaleItem(ActionEvent actionEvent) {

        Optional<Pair<String,Float>> result = newSaleItemDialog.showAndWait();

        result.ifPresent(pair -> peer.addSaleItem(
                new SaleItem()
                .setDescription(pair.getKey())
                .setPrice(pair.getValue())
        ));
    }

    public void onSearchItem(ActionEvent actionEvent) {
        Optional<String> result = searchItemDialog.showAndWait();
        result.ifPresent(peer::searchItemDescription);
    }

    public void exitApplication(WindowEvent event){
        peer.disconnect();
        Platform.exit();
    }

    public void setPeer(Peer peer) {
        this.peer = peer;
        peer.addMulticastMessageEventListener(message->/*{
            synchronized(messageLock) {*/
                textAreaOutput.appendText(message.concat("\n"))//;
        /*}}*/);
                peer.addIndexerConnectionEventListener(connected->searchItemButton.setDisable(!connected));
            }

    private Dialog<Pair<String, Float>> buildNewSaleItemDialog() {
        Dialog<Pair<String, Float>> newSaleItemDialog = new Dialog<>();
        newSaleItemDialog.setTitle("New Sale Item Dialog");
        newSaleItemDialog.setHeaderText("Please, enter the to be created item's description and price");

        ButtonType newSaleItemDialogButtonType = new ButtonType("Send", ButtonBar.ButtonData.OK_DONE);
        newSaleItemDialog.getDialogPane().getButtonTypes().addAll(newSaleItemDialogButtonType, ButtonType.CANCEL);

        GridPane newSaleItemDialogGrid = new GridPane();
        newSaleItemDialogGrid.setHgap(10);
        newSaleItemDialogGrid.setVgap(10);
        newSaleItemDialogGrid.setPadding(new Insets(20,150,10,10));

        TextField newSaleItemDialogDescriptionField = new TextField();
        newSaleItemDialogDescriptionField.setPromptText("Description");
        newSaleItemDialogDescriptionField.setText("Item Description");
        TextField newSaleItemDialogPriceField = new TextField();
        newSaleItemDialogPriceField.setPromptText("Price");
        StringConverter<Float> stringConverter = new StringConverter<Float>() {
            @Override
            public String toString(Float object) {
                return String.format("$%1.2f", object);
            }

            @Override
            public Float fromString(String string) {
                return Float.parseFloat(string.replace('$',' ').trim());
            }
        };
        UnaryOperator<TextFormatter.Change> filter = change -> {
            if(!change.isContentChange())
                return change;
            String oldText = change.getControlText();
            String newText = change.getControlNewText();
            if(!newText.matches("^\\$(?=.*\\d)\\d+\\.\\d{2}$")) {
                if(newText.matches("^\\$(?=.*\\d)\\d+\\.\\d?$")) {
                    change.setRange(0,oldText.length());
                    int init = change.getCaretPosition();
                    String firstHalf = newText.substring(0, init);
                    String secondHalf = newText.substring(init);
                    String inserted = newText.matches(".*\\.\\d$")?"0":"00";
                    newText = firstHalf+inserted+secondHalf;
                    change.setText(newText);
                    change.selectRange(init, init+inserted.length());
                    return change;
                }
                return null;
            }
            return change;
        };
        newSaleItemDialogPriceField.setTextFormatter(new TextFormatter<>(stringConverter, 1.0F, filter));

        newSaleItemDialogGrid.add(new Label("Description:"), 0, 0);
        newSaleItemDialogGrid.add(newSaleItemDialogDescriptionField, 1, 0);
        newSaleItemDialogGrid.add(new Label("Price:"), 0, 1);
        newSaleItemDialogGrid.add(newSaleItemDialogPriceField, 1, 1);

        Node newSaleItemDialogButton = newSaleItemDialog.getDialogPane().lookupButton(newSaleItemDialogButtonType);
        newSaleItemDialogButton.setDisable(newSaleItemDialogDescriptionField.getText().isEmpty());

        newSaleItemDialogDescriptionField.textProperty()
                .addListener((observable, oldValue, newValue) ->
                    newSaleItemDialogButton.setDisable(
                        newValue.trim().isEmpty() || newValue.matches(".*/.*" ) ||
                        (Float) newSaleItemDialogPriceField.getTextFormatter().getValue() < 0.01f
                    ));

        newSaleItemDialogPriceField.textProperty()
                .addListener((observable, oldValue, newValue) ->
                    newSaleItemDialogButton.setDisable(
                            stringConverter.fromString(newValue) < 0.01f ||
                            newSaleItemDialogDescriptionField.getText().trim().isEmpty() ||
                            newSaleItemDialogDescriptionField.getText().matches(".*/.*")
                    ));

        newSaleItemDialog.getDialogPane().setContent(newSaleItemDialogGrid);
        Platform.runLater(newSaleItemDialogDescriptionField::requestFocus);

        newSaleItemDialog.setResultConverter( button ->
                button.equals(newSaleItemDialogButtonType)?
                        new Pair<>(newSaleItemDialogDescriptionField.getText(),
                                (Float)newSaleItemDialogPriceField.getTextFormatter().getValue()) : null
        );

        newSaleItemDialog.setContentText("Please enter your name:");
        return newSaleItemDialog;
    }

    private TextInputDialog buildSearchItemDialog() {
        TextInputDialog searchItemDialog = new TextInputDialog("item");
        searchItemDialog.setTitle("Search item");
        searchItemDialog.setHeaderText("Please, enter the description of the desired item to be bought");
        searchItemDialog.setContentText("Item Description: ");

        searchItemDialog.getEditor().textProperty()
                .addListener( (observable, oldValue, newValue) ->
                        searchItemDialog.getDialogPane().lookupButton(ButtonType.OK).setDisable(
                                newValue.trim().isEmpty() || newValue.matches(".*/.*"))
        );

        return searchItemDialog;
    }

}
