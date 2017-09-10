package edu.utfpr.guilhermej.sisdist.av1;

import edu.utfpr.guilhermej.sisdist.av1.controller.PeerWindowController;
import edu.utfpr.guilhermej.sisdist.av1.model.Peer;
import javafx.application.Application;
import javafx.event.EventHandler;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;
import javafx.stage.WindowEvent;

import java.util.Locale;

public class Main extends Application {

    @Override
    public void start(Stage primaryStage) throws Exception{
        //Garante correta conversao de float em string separadas por ponto ao inves de virgula
        Locale.setDefault(new Locale("en", "US"));

        //Carregando View
        FXMLLoader loader = new FXMLLoader(getClass().getResource("view/peerWindow.fxml"));
        Parent root = loader.load();

        //Inicializando modelo e injetando no controlador
        Peer peer = new Peer();
        PeerWindowController controller = (PeerWindowController) loader.getController();
        controller.setPeer(peer);

        //Configurando janela da view
        primaryStage.setResizable(false);
        primaryStage.setTitle("SD1-"+peer.toString());
        primaryStage.setScene(new Scene(root, 800, 450));
        primaryStage.setOnCloseRequest(new EventHandler<WindowEvent>() {
            @Override
            public void handle(WindowEvent event) {
                controller.exitApplication(event);
            }
        });
        primaryStage.show();
    }

    @Override
    public void stop() throws Exception {
        System.out.printf("Stage closing");
        super.stop();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
