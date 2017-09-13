package edu.utfpr.guilhermej.sisdist.av1.controller;

import edu.utfpr.guilhermej.sisdist.av1.model.Peer;
import edu.utfpr.guilhermej.sisdist.av1.model.PeerOpponent;
import edu.utfpr.guilhermej.sisdist.av1.model.SaleItem;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.GridPane;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.stage.WindowEvent;
import javafx.util.Pair;
import javafx.util.StringConverter;

import java.util.Observable;
import java.util.Optional;
import java.util.function.UnaryOperator;

public class PeerWindowController {
    public Button newSaleItemButton;
    public TextArea textAreaOutput;
    public Button searchItemButton;
    public Label moneyLabel;
    public Circle conectionBulb;
    public Label conectionLabel;
    public ListView saleItemsListView;

    private ObservableList<SaleItem> saleItemsList;

    private Dialog<Pair<String, Float>> newSaleItemDialog = null;
    private TextInputDialog searchItemDialog = null;
    private Alert itemFoundAlert = null;
    private Alert itemNotFoundAlert = null;
    private Alert itemBoughtAlert = null;
    private Alert itemSoldAlert = null;

    private final Object messageLock;

    private Peer peer;

    public PeerWindowController(){
        messageLock = new Object();
    }

    public void initialize(){
        newSaleItemDialog = buildNewSaleItemDialog();
        searchItemDialog = buildSearchItemDialog();
        itemFoundAlert = buildItemFoundAlert();
        itemNotFoundAlert = buildItemNotFoundAlert();
        itemBoughtAlert = buildItemBoughtOrSoldAlert();
        itemSoldAlert = buildItemBoughtOrSoldAlert();

        saleItemsList = FXCollections.observableArrayList();
        saleItemsListView.setItems(saleItemsList);
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
        moneyLabel.setText(getMoneyText(peer.getMoney()));
        peer.addIndexerConnectionEventListener(connected -> Platform.runLater(()->{
            searchItemButton.setDisable(!connected);
            conectionLabel.setText(connected?
                    "Connected":
                    "Disconnected");
            conectionBulb.setFill(connected?
                    Color.GREENYELLOW:
                    Color.ORANGERED);

        }));
        peer.addMoneyListener(value -> Platform.runLater(() -> {
                moneyLabel.setText(getMoneyText(value));
        }));
        peer.addMulticastMessageEventListener(message-> Platform.runLater(()->{
            synchronized(messageLock) {
                textAreaOutput.appendText(message.concat("\n"));
            }}));
        peer.addItemProposalEventListener(event->Platform.runLater(()->{
            switch (event.getProposalStage()) {
                case FOUND:
                    Optional<ButtonType> result = showItemFoundAlertAndWait(itemFoundAlert, event.getItem(), event.getSeller());
                    if (result.isPresent() && result.get() == ButtonType.OK)
                        event.accept();
                    else
                        event.reject();
                break;
                case NOT_FOUND:
                    showItemNotFoundAlertAndWait(itemNotFoundAlert, event.getItem());
                    break;
                case ITEM_BOUGHT:
                    showItemBoughtAlertAndWait(itemBoughtAlert, event.getItem());
                    break;
                case ITEM_SOLD:
                    showItemSoldAlertAndWait(itemSoldAlert, event.getItem());
                    break;
            }
        }));
        peer.addItemListEventListener(event->Platform.runLater(()->{
            switch (event.getType()){
                case ADDED:
                    saleItemsList.add(event.getItem());
                    break;
                case REMOVED:
                    saleItemsList.remove(event.getItem());
                    break;
                case MODIFIED:
                    break;
            }
        }));
    }

    private String getMoneyText(float value) {
        return String.format("%.02f", value);
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
        newSaleItemDialogPriceField.setTextFormatter(new TextFormatter<>(stringConverter, 9.99F, filter));

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

    private Alert buildItemFoundAlert(){
        Alert itemProposalAlert = new Alert(Alert.AlertType.CONFIRMATION);
        itemProposalAlert.setTitle("Item Found");
        itemProposalAlert.setContentText("Confirm purchase?");
        return itemProposalAlert;
    }

    private Alert buildItemNotFoundAlert(){
        Alert itemNotFoundAlert = new Alert(Alert.AlertType.INFORMATION);
        itemNotFoundAlert.setTitle("Item not Found");
        itemNotFoundAlert.setHeaderText(null);
        return itemNotFoundAlert;
    }

    private Alert buildItemBoughtOrSoldAlert(){
        Alert itemBoughtorSoldAlert = new Alert((Alert.AlertType.INFORMATION));
        itemBoughtorSoldAlert.setTitle("Transaction Successful");
        itemNotFoundAlert.setHeaderText(null);
        return  itemBoughtorSoldAlert;
    }

    private Optional<ButtonType> showItemFoundAlertAndWait(Alert itemProposalAlert, SaleItem item, PeerOpponent seller){
        itemProposalAlert.setHeaderText(String.format("Found item \"%s\" for $%01.02f with %s:%s.",
                item.getDescription(), item.getPrice(), seller.getIpAddress(), seller.getPortTcp()));
        return itemProposalAlert.showAndWait();
    }

    private Optional<ButtonType> showItemNotFoundAlertAndWait(Alert itemNotFound, SaleItem item){
        if(item != null)
            itemNotFound.setContentText(String.format("Item \"%s\" not found.", item.getDescription()));
        else
            itemNotFound.setContentText("Item not found");
        return itemNotFound.showAndWait();
    }

    private Optional<ButtonType> showItemBoughtAlertAndWait(Alert itemNotFound, SaleItem item){
        itemNotFound.setContentText(String.format("You have successfully bought \"%s\" for $%01.02f!",
                item.getDescription(), item.getPrice()));
        return itemNotFound.showAndWait();
    }

    private Optional<ButtonType> showItemSoldAlertAndWait(Alert itemNotFound, SaleItem item){
        itemNotFound.setContentText(String.format("You have just sold \"%s\" for $%01.02f!",
                item.getDescription(), item.getPrice()));
        return itemNotFound.showAndWait();
    }
}
