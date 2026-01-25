package com.fazenda;

import javafx.animation.AnimationTimer;
import javafx.application.Application;
import javafx.scene.Scene;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.image.Image;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.stage.Stage;

import java.util.HashSet;
import java.util.Set;

public class IndustrialFarmGame extends Application {

    private static final int TILE_SIZE = 24;
    private static final int WIDTH = 1000;
    private static final int HEIGHT = 700;

    private Image tractorSheet;
    private final double SPRITE_W = 256;
    private final double SPRITE_H = 256;
    private final double TRACTOR_SCALE = 0.4;

    private double tractorX = 500, tractorY = 500;
    private double angle = 45; // Começa no Sul Isométrico
    private double currentSpeed = 0;

    private final double MAX_SPEED = 2.5;
    private final double ACCELERATION = 0.05;
    private final double FRICTION = 0.03;
    private final double ROTATION_SPEED = 2.5; // Um pouco mais rápido para melhor resposta

    private double cameraX, cameraY;
    private Set<KeyCode> activeKeys = new HashSet<>();
    private int[][] farmMap = new int[100][100];

    @Override
    public void start(Stage stage) {
        try {
            tractorSheet = new Image(getClass().getResourceAsStream("/trator.png"));
        } catch (Exception e) {
            System.err.println("Erro: 'trator.png' não encontrado.");
        }

        Canvas canvas = new Canvas(WIDTH, HEIGHT);
        GraphicsContext gc = canvas.getGraphicsContext2D();
        StackPane root = new StackPane(canvas);
        Scene scene = new Scene(root);

        scene.setOnKeyPressed(e -> activeKeys.add(e.getCode()));
        scene.setOnKeyReleased(e -> activeKeys.remove(e.getCode()));

        new AnimationTimer() {
            @Override
            public void handle(long now) {
                update();
                render(gc);
            }
        }.start();

        stage.setTitle("Farm Simulator - Orientação Isométrica Corrigida");
        stage.setScene(scene);
        stage.show();
    }

    private void update() {
        if (activeKeys.contains(KeyCode.W))
            currentSpeed = Math.min(currentSpeed + ACCELERATION, MAX_SPEED);
        else if (activeKeys.contains(KeyCode.S))
            currentSpeed = Math.max(currentSpeed - ACCELERATION, -MAX_SPEED / 2);
        else
            currentSpeed *= (1 - FRICTION);

        if (Math.abs(currentSpeed) > 0.1) {
            double directionFactor = currentSpeed > 0 ? 1 : -1;
            if (activeKeys.contains(KeyCode.A))
                angle -= ROTATION_SPEED * directionFactor;
            if (activeKeys.contains(KeyCode.D))
                angle += ROTATION_SPEED * directionFactor;
        }

        double radians = Math.toRadians(angle);
        tractorX += Math.cos(radians) * currentSpeed;
        tractorY += Math.sin(radians) * currentSpeed;

        int tx = (int) (tractorX / TILE_SIZE);
        int ty = (int) (tractorY / TILE_SIZE);
        if (ty >= 0 && ty < farmMap.length && tx >= 0 && tx < farmMap[0].length) {
            farmMap[ty][tx] = 1;
        }

        cameraX = (tractorX - tractorY) - WIDTH / 2.0;
        cameraY = (tractorX + tractorY) / 2.0 - HEIGHT / 2.0;
    }

    private void render(GraphicsContext gc) {
        gc.setFill(Color.web("#1e3a13"));
        gc.fillRect(0, 0, WIDTH, HEIGHT);

        gc.save();
        gc.translate(-cameraX, -cameraY);

        for (int row = 0; row < farmMap.length; row++) {
            for (int col = 0; col < farmMap[row].length; col++) {
                double isoX = (col * TILE_SIZE - row * TILE_SIZE);
                double isoY = (col * TILE_SIZE + row * TILE_SIZE) / 2.0;
                if (isoX > cameraX - 100 && isoX < cameraX + WIDTH + 100 &&
                        isoY > cameraY - 100 && isoY < cameraY + HEIGHT + 100) {
                    drawIsoTile(gc, isoX, isoY, farmMap[row][col] == 1 ? Color.web("#3d2611") : Color.web("#2d4c21"));
                }
            }
        }

        renderIsoTractor(gc, (tractorX - tractorY), (tractorX + tractorY) / 2.0);
        gc.restore();

        renderCompass(gc);
        renderUI(gc);
    }

    private void drawIsoTile(GraphicsContext gc, double x, double y, Color color) {
        gc.setFill(color);
        gc.setStroke(Color.web("#000000", 0.05));
        double[] xs = { x, x + TILE_SIZE, x, x - TILE_SIZE };
        double[] ys = { y, y + TILE_SIZE / 2.0, y + TILE_SIZE, y + TILE_SIZE / 2.0 };
        gc.fillPolygon(xs, ys, 4);
        gc.strokePolygon(xs, ys, 4);
    }

    private void renderIsoTractor(GraphicsContext gc, double x, double y) {
        if (tractorSheet == null)
            return;

        // Normaliza o ângulo entre 0 e 360
        double normalizedAngle = (angle % 360 + 360) % 360;

        int spritePos;

        // Agora os limites são as "quinas" (45, 135, 225, 315)
        // Cada imagem agora cobre um quadrante centralizado nos eixos isométricos
        if (normalizedAngle >= 45 && normalizedAngle < 135) {
            spritePos = 2; // OESTE (Diagonal inferior esquerda)
        } else if (normalizedAngle >= 135 && normalizedAngle < 225) {
            spritePos = 3; // NORTE (Diagonal superior esquerda)
        } else if (normalizedAngle >= 225 && normalizedAngle < 315) {
            spritePos = 1; // LESTE (Diagonal superior direita)
        } else {
            // De 315 a 360 E de 0 a 45
            spritePos = 0; // SUL (Diagonal inferior direita)
        }

        int col = spritePos % 2;
        int row = spritePos / 2;
        double drawW = SPRITE_W * TRACTOR_SCALE;
        double drawH = SPRITE_H * TRACTOR_SCALE;

        gc.save();
        gc.translate(x, y);

        // Desenha o trator centralizado no ponto X, Y
        gc.drawImage(tractorSheet,
                col * SPRITE_W, row * SPRITE_H, SPRITE_W, SPRITE_H,
                -drawW / 2.0, -drawH * 0.85,
                drawW, drawH);
        gc.restore();
    }

    private void renderCompass(GraphicsContext gc) {
        double cx = WIDTH - 80, cy = 80, r = 50;
        gc.setFill(Color.web("#333333", 0.8));
        gc.fillOval(cx - r, cy - r, r * 2, r * 2);

        gc.save();
        gc.translate(cx, cy);
        gc.rotate(angle + 135);

        gc.setFill(Color.RED);
        gc.fillPolygon(new double[] { -6, 0, 6 }, new double[] { 0, -r + 12, 0 }, 3);
        gc.setFill(Color.WHITE);
        gc.fillPolygon(new double[] { -6, 0, 6 }, new double[] { 0, r - 12, 0 }, 3);
        gc.restore();
    }

    private void renderUI(GraphicsContext gc) {
        gc.setFill(Color.WHITE);
        gc.fillText("Ângulo: " + (int) ((angle % 360 + 360) % 360) + "°", 20, 30);
    }

    public static void main(String[] args) {
        launch(args);
    }
}