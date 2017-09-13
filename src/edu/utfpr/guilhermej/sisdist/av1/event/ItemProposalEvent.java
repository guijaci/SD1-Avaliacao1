package edu.utfpr.guilhermej.sisdist.av1.event;

import edu.utfpr.guilhermej.sisdist.av1.model.PeerOpponent;
import edu.utfpr.guilhermej.sisdist.av1.model.SaleItem;

import java.util.function.BiConsumer;

public class ItemProposalEvent {
    private final ProposalStage proposalStage;
    private final SaleItem item;
    private final PeerOpponent seller;
    private final BiConsumer<SaleItem, PeerOpponent> onAccept;
    private final BiConsumer<SaleItem, PeerOpponent> onReject;

    public enum ProposalStage{
        FOUND, NOT_FOUND, ITEM_SOLD, ITEM_BOUGHT
    }

    private ItemProposalEvent(ProposalStage proposalStage, SaleItem item, PeerOpponent seller, BiConsumer<SaleItem, PeerOpponent> onAccept, BiConsumer<SaleItem, PeerOpponent> onReject){
        this.proposalStage = proposalStage;
        this.item = item;
        this.seller = seller;
        this.onAccept = onAccept;
        this.onReject = onReject;
    }

    public static ItemProposalEvent itemFound(SaleItem item, PeerOpponent seller, BiConsumer<SaleItem, PeerOpponent> onAccept, BiConsumer<SaleItem, PeerOpponent> onReject){
        if(item == null || seller == null || onAccept == null)
            throw new IllegalArgumentException("Null argument");
        return new ItemProposalEvent(ProposalStage.FOUND, item, seller, onAccept, onReject);
    }

    public static ItemProposalEvent itemFound(SaleItem item, PeerOpponent seller, BiConsumer<SaleItem, PeerOpponent> onAccept){
        if(item == null || seller == null || onAccept == null)
            throw new IllegalArgumentException("Null argument");
        return new ItemProposalEvent(ProposalStage.FOUND, item, seller, onAccept, null);
    }

    public static ItemProposalEvent itemNotFound(SaleItem item){
        if(item == null)
            throw new IllegalArgumentException("Null argument");
        return new ItemProposalEvent(ProposalStage.NOT_FOUND, item, null, null, null);
    }

    public static ItemProposalEvent itemNotFound(){
        return new ItemProposalEvent(ProposalStage.NOT_FOUND, null, null, null, null);
    }

    public static ItemProposalEvent itemSold(SaleItem item, PeerOpponent seller){
        if(item == null || seller == null)
            throw new IllegalArgumentException("Null argument");
        return new ItemProposalEvent(ProposalStage.ITEM_SOLD, item, seller, null, null);
    }

    public static ItemProposalEvent itemBought(SaleItem item, PeerOpponent seller){
        if(item == null || seller == null)
            throw new IllegalArgumentException("Null argument");
        return new ItemProposalEvent(ProposalStage.ITEM_BOUGHT, item, seller, null, null);
    }

    public SaleItem getItem() {
        return item;
    }

    public PeerOpponent getSeller() {
        return seller;
    }

    public ProposalStage getProposalStage() {
        return proposalStage;
    }

    public void accept() {
        if(onAccept != null)
            onAccept.accept(item, seller);
    }

    public void reject() {
        if(onReject != null)
            onReject.accept(item, seller);
    }
}
