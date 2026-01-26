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
import javafx.scene.shape.ArcType;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.stage.Stage;

import java.util.HashSet;
import java.util.Set;

public class IndustrialFarmGame extends Application {

    private static final int TILE_SIZE = 12;
    private static final int WIDTH = 1000;
    private static final int HEIGHT = 700;
    private static final int MAP_SIZE = 200;

    private int[][] savedMap = null;

    private Image tractorSheet;
    private final double SPRITE_W = 931.0 / 6.0;
    private final double SPRITE_H = 472.0 / 4.0;
    private final double TRACTOR_SCALE = 0.4;

    private double tractorX = 500, tractorY = 500;
    private double angle = 45, smoothedAngle = 45;
    private double currentSpeed = 0;

    private final double MAX_SPEED_KMH = 30.0;
    private final double MAX_SPEED = 1.8;
    private final double ACCELERATION = 0.005;
    private final double FRICTION = 0.015;
    private final double BRAKE_FORCE = 0.04;
    private final double BASE_ROTATION = 1.2;

    private double trailerX = 430, trailerY = 430;
    private double trailerAngle = 45;
    private final double TRAILER_DISTANCE = 60.0;
    private boolean isAttached = false;

    private double cameraX, cameraY;
    private Set<KeyCode> activeKeys = new HashSet<>();
    private int[][] farmMap = new int[MAP_SIZE][MAP_SIZE];

    @Override
    public void start(Stage stage) {
        try {
            tractorSheet = new Image(getClass().getResourceAsStream("/trator.png"));
        } catch (Exception e) {
            System.err.println("Erro: 'trator.png' não encontrado.");
        }

        if (savedMap != null) {
            for (int r = 0; r < Math.min(savedMap.length, MAP_SIZE); r++) {
                for (int c = 0; c < Math.min(savedMap[r].length, MAP_SIZE); c++) {
                    farmMap[r][c] = savedMap[r][c];
                }
            }
        }

        Canvas canvas = new Canvas(WIDTH, HEIGHT);
        GraphicsContext gc = canvas.getGraphicsContext2D();
        StackPane root = new StackPane(canvas);
        Scene scene = new Scene(root);

        canvas.setOnMouseDragged(e -> editMap(e.getX(), e.getY(), e.isPrimaryButtonDown()));
        canvas.setOnMousePressed(e -> editMap(e.getX(), e.getY(), e.isPrimaryButtonDown()));

        scene.setOnKeyPressed(e -> {
            activeKeys.add(e.getCode());
            if (e.getCode() == KeyCode.P)
                exportMapToCode();
            if (e.getCode() == KeyCode.C)
                toggleCouping();
        });
        scene.setOnKeyReleased(e -> activeKeys.remove(e.getCode()));

        new AnimationTimer() {
            @Override
            public void handle(long now) {
                update();
                render(gc);
            }
        }.start();

        stage.setTitle("Farm Simulator - C: Acoplar/Desacoplar | P: Exportar");
        stage.setScene(scene);
        stage.show();
    }

    private void toggleCouping() {
        if (isAttached) {
            isAttached = false;
        } else {
            double backX = tractorX - Math.cos(Math.toRadians(angle)) * 30;
            double backY = tractorY - Math.sin(Math.toRadians(angle)) * 30;
            double dx = backX - trailerX;
            double dy = backY - trailerY;
            double dist = Math.sqrt(dx * dx + dy * dy);
            if (dist < 35)
                isAttached = true;
        }
    }

    private void editMap(double mouseX, double mouseY, boolean isAdd) {
        double worldX = mouseX + cameraX;
        double worldY = mouseY + cameraY;
        int col = (int) ((worldY + worldX / 2.0) / TILE_SIZE);
        int row = (int) ((worldY - worldX / 2.0) / TILE_SIZE);
        if (row >= 0 && row < MAP_SIZE && col >= 17 && col < MAP_SIZE) {
            farmMap[row][col] = isAdd ? 1 : 0;
        }
    }

    private void exportMapToCode() {
        System.out.println("\n--- CÓDIGO EXPORTADO ---");
        StringBuilder sb = new StringBuilder("savedMap = new int[][]{\n");
        for (int r = 0; r < MAP_SIZE; r++) {
            boolean hasData = false;
            for (int c = 0; c < MAP_SIZE; c++) {
                if (farmMap[r][c] == 1) {
                    hasData = true;
                    break;
                }
            }
            if (hasData) {
                sb.append("  {");
                for (int c = 0; c < MAP_SIZE; c++) {
                    sb.append(farmMap[r][c]).append(c == MAP_SIZE - 1 ? "" : ",");
                }
                sb.append("},\n");
            }
        }
        sb.append("};");
        System.out.println(sb.toString());
    }

    private void update() {
        if (activeKeys.contains(KeyCode.W))
            currentSpeed = Math.min(currentSpeed + ACCELERATION, MAX_SPEED);
        else if (activeKeys.contains(KeyCode.S)) {
            if (currentSpeed > 0)
                currentSpeed = Math.max(currentSpeed - BRAKE_FORCE, 0);
            else
                currentSpeed = Math.max(currentSpeed - ACCELERATION, -MAX_SPEED / 3.0);
        } else {
            currentSpeed *= (1 - FRICTION);
            if (Math.abs(currentSpeed) < 0.005)
                currentSpeed = 0;
        }

        if (Math.abs(currentSpeed) > 0.01) {
            double dir = currentSpeed > 0 ? 1 : -1;
            double turnAbility = Math.min(Math.abs(currentSpeed) * 1.2, BASE_ROTATION);
            if (activeKeys.contains(KeyCode.A))
                angle -= turnAbility * dir;
            if (activeKeys.contains(KeyCode.D))
                angle += turnAbility * dir;
        }

        double diff = angle - smoothedAngle;
        while (diff < -180) diff += 360;
        while (diff > 180) diff -= 360;
        smoothedAngle += diff * 0.25;

        double radians = Math.toRadians(angle);
        double nextX = tractorX + Math.cos(radians) * currentSpeed;
        double nextY = tractorY + Math.sin(radians) * currentSpeed;

        if (!isAttached) {
            double dx = nextX - trailerX;
            double dy = nextY - trailerY;
            double distance = Math.sqrt(dx * dx + dy * dy);
            if (distance < 45) {
                double pushAngle = Math.atan2(dy, dx);
                nextX = trailerX + Math.cos(pushAngle) * 45;
                nextY = trailerY + Math.sin(pushAngle) * 45;
                currentSpeed *= 0.5;
            }
        }

        tractorX = nextX;
        tractorY = nextY;

        if (isAttached) {
            double dx = tractorX - trailerX;
            double dy = tractorY - trailerY;
            double dist = Math.sqrt(dx * dx + dy * dy);
            double angleToTractor = Math.toDegrees(Math.atan2(dy, dx));

            if (dist != TRAILER_DISTANCE) {
                double diffDist = dist - TRAILER_DISTANCE;
                trailerX += Math.cos(Math.atan2(dy, dx)) * diffDist;
                trailerY += Math.sin(Math.atan2(dy, dx)) * diffDist;

                double adiff = angleToTractor - trailerAngle;
                while (adiff < -180) adiff += 360;
                while (adiff > 180) adiff -= 360;

                if (Math.abs(currentSpeed) > 0.05) {
                    double factor = (currentSpeed > 0) ? 0.15 : 0.4;
                    trailerAngle += adiff * factor;
                }
            }

            if (Math.abs(currentSpeed) > 0.1) {
                int tx = (int) (trailerX / TILE_SIZE);
                int ty = (int) (trailerY / TILE_SIZE);
                for (int i = -3; i <= 3; i++) {
                    for (int j = -3; j <= 3; j++) {
                        int nx = tx + i, ny = ty + j;
                        if (ny >= 0 && ny < MAP_SIZE && nx >= 17 && nx < MAP_SIZE)
                            farmMap[ny][nx] = 1;
                    }
                }
            }
        }

        cameraX = (tractorX - tractorY) - WIDTH / 2.0;
        cameraY = (tractorX + tractorY) / 2.0 - HEIGHT / 2.0;
    }

    private void render(GraphicsContext gc) {
        gc.setFill(Color.web("#142a0d"));
        gc.fillRect(0, 0, WIDTH, HEIGHT);

        gc.save();
        gc.translate(-cameraX, -cameraY);

        int centerCol = (int) (tractorX / TILE_SIZE);
        int centerRow = (int) (tractorY / TILE_SIZE);
        int viewRadius = 45;

        for (int row = Math.max(0, centerRow - viewRadius); row <= Math.min(MAP_SIZE - 1, centerRow + viewRadius); row++) {
            for (int col = Math.max(0, centerCol - viewRadius); col <= Math.min(MAP_SIZE - 1, centerCol + viewRadius); col++) {
                double isoX = (col * TILE_SIZE - row * TILE_SIZE);
                double isoY = (col * TILE_SIZE + row * TILE_SIZE) / 2.0;

                if (isoX > cameraX - 100 && isoX < cameraX + WIDTH + 100 && isoY > cameraY - 100 && isoY < cameraY + HEIGHT + 100) {
                    
                    if (col < 2) gc.setFill(Color.web("#808080")); 
                    else if (col >= 2 && col < 10) gc.setFill(Color.web("#2c2c2c")); 
                    else if (col >= 10 && col < 12) gc.setFill(Color.web("#808080")); 
                    else if (col >= 12 && col < 17) gc.setFill((row + col) % 2 == 0 ? Color.web("#1b3011") : Color.web("#1e3613"));
                    else gc.setFill(farmMap[row][col] == 1 ? Color.web("#3d2611") : Color.web("#2d4c21"));

                    gc.fillPolygon(new double[] { isoX, isoX + TILE_SIZE, isoX, isoX - TILE_SIZE },
                                   new double[] { isoY, isoY + TILE_SIZE / 2, isoY + TILE_SIZE, isoY + TILE_SIZE / 2 }, 4);
                }
            }
        }

        // --- LINHAS DE TRÂNSITO VETORIAIS (Contínuas de fora a fora) ---
        drawRoadLine(gc, 5.8, Color.web("#f1c40f"), 2); // Linha amarela 1 (centro)
        drawRoadLine(gc, 6.2, Color.web("#f1c40f"), 2); // Linha amarela 2 (centro)
        drawRoadLine(gc, 2.2, Color.WHITE, 1.5);       // Linha branca (acostamento esquerdo)

        if (isAttached) {
            double xTractor = (tractorX - tractorY);
            double yTractor = (tractorX + tractorY) / 2.0 - 10;
            double frontOffset = 5;
            double frontX = trailerX + Math.cos(Math.toRadians(trailerAngle)) * frontOffset;
            double frontY = trailerY + Math.sin(Math.toRadians(trailerAngle)) * frontOffset;
            double xTrailerFront = (frontX - frontY);
            double yTrailerFront = (frontX + frontY) / 2.0;
            gc.setStroke(Color.BLACK);
            gc.setLineWidth(3);
            gc.strokeLine(xTractor, yTractor, xTrailerFront, yTrailerFront);
        }

        renderPlantadoraIso(gc, trailerX, trailerY, trailerAngle);
        renderIsoTractor(gc, (tractorX - tractorY), (tractorX + tractorY) / 2.0);
        gc.restore();

        renderMiniMap(gc);
        renderSpeedometer(gc);

        gc.setFill(Color.WHITE);
        gc.setFont(Font.font("Arial", FontWeight.BOLD, 14));
        gc.fillText("Trailer: " + (isAttached ? "CONECTADO" : "SOLTO (C para acoplar)"), 20, 30);
    }

    // Desenha uma linha isométrica contínua ao longo de todo o mapa
    private void drawRoadLine(GraphicsContext gc, double colPos, Color color, double width) {
        gc.setStroke(color);
        gc.setLineWidth(width);
        double xStart = (colPos * TILE_SIZE - 0 * TILE_SIZE);
        double yStart = (colPos * TILE_SIZE + 0 * TILE_SIZE) / 2.0;
        double xEnd = (colPos * TILE_SIZE - (MAP_SIZE) * TILE_SIZE);
        double yEnd = (colPos * TILE_SIZE + (MAP_SIZE) * TILE_SIZE) / 2.0;
        gc.strokeLine(xStart, yStart + TILE_SIZE/2.0, xEnd, yEnd + TILE_SIZE/2.0);
    }

    private void renderPlantadoraIso(GraphicsContext gc, double tx, double ty, double tAngle) {
        double isoX = tx - ty;
        double isoY = (tx + ty) / 2.0;
        gc.save();
        gc.translate(isoX, isoY);
        gc.rotate(tAngle + 45);
        gc.setFill(Color.web("#1a4a7a"));
        gc.fillRect(-7, -45, 12, 90);
        gc.setFill(Color.web("#000000", 0.2));
        gc.fillRect(-7, -45, 4, 90);
        for (int i = -40; i <= 40; i += 13) {
            gc.setFill(Color.web("#222222"));
            gc.fillRect(-10, i - 1, 6, 2);
            gc.setFill(Color.web("#777777"));
            gc.fillOval(-14, i - 4, 8, 8);
            gc.setFill(Color.web("#eeeeee", 0.5));
            gc.fillOval(-12, i - 2, 3, 3);
            gc.setFill(Color.web("#143a5f"));
            gc.fillRect(-8, i - 4, 5, 8);
        }
        gc.setFill(Color.web("#222222"));
        gc.fillOval(-5, -51, 10, 6);
        gc.fillOval(-5, 45, 10, 6);
        gc.restore();
    }

    private void renderIsoTractor(GraphicsContext gc, double x, double y) {
        if (tractorSheet == null) return;
        double visualAngle = (smoothedAngle % 360 + 360) % 360;
        double frameAngle = (90 - visualAngle + 360) % 360;
        int index = (int) Math.floor((frameAngle + 7.5) / 15.0) % 24;
        double drawW = SPRITE_W * TRACTOR_SCALE, drawH = SPRITE_H * TRACTOR_SCALE;
        gc.save();
        gc.translate(x, y);
        gc.drawImage(tractorSheet, (index % 6) * SPRITE_W, (index / 6) * SPRITE_H, SPRITE_W, SPRITE_H, -drawW / 2.0,
                -drawH * 0.85, drawW, drawH);
        gc.restore();
    }

    private void renderMiniMap(GraphicsContext gc) {
        double size = 140, x = WIDTH - size - 20, y = 20;
        gc.setFill(Color.web("#111111", 0.85));
        gc.fillOval(x, y, size, size);
        gc.save();
        gc.beginPath();
        gc.arc(x + size / 2, y + size / 2, size / 2, size / 2, 0, 360);
        gc.clip();
        double vRad = 25, mTile = size / (vRad * 2);
        int sx = (int) (tractorX / TILE_SIZE - vRad), sy = (int) (tractorY / TILE_SIZE - vRad);
        gc.setFill(Color.web("#5d3a1a"));
        for (int i = 0; i < vRad * 2; i++) {
            for (int j = 0; j < vRad * 2; j++) {
                int mx = sx + i, my = sy + j;
                if (my >= 0 && my < MAP_SIZE && mx >= 0 && mx < MAP_SIZE && farmMap[my][mx] == 1)
                    gc.fillRect(x + i * mTile, y + j * mTile, mTile + 1, mTile + 1);
            }
        }
        gc.setFill(Color.YELLOW);
        gc.fillOval(x + size / 2 - 3, y + size / 2 - 3, 6, 6);
        gc.restore();
        gc.setStroke(Color.WHITE);
        gc.strokeOval(x, y, size, size);
    }

    private void renderSpeedometer(GraphicsContext gc) {
        double cx = 120, cy = HEIGHT - 100, r = 90;
        gc.setFill(Color.web("#111111", 0.9));
        gc.fillArc(cx - r, cy - r, r * 2, r * 2, 0, 180, ArcType.ROUND);
        gc.setStroke(Color.WHITE);
        gc.setLineWidth(1.5);
        for (int i = 0; i <= 180; i += 30) {
            double rad = Math.toRadians(180 - i);
            gc.strokeLine(cx + Math.cos(rad) * (r - 5), cy - Math.sin(rad) * (r - 5), cx + Math.cos(rad) * r,
                    cy - Math.sin(rad) * r);
            int speedVal = (int) ((i / 180.0) * MAX_SPEED_KMH);
            gc.setFill(Color.GRAY);
            gc.setFont(Font.font("Arial", 10));
            gc.fillText(String.valueOf(speedVal), cx + Math.cos(rad) * (r - 18) - 5, cy - Math.sin(rad) * (r - 18) + 5);
        }
        double speedKmh = (Math.abs(currentSpeed) / MAX_SPEED) * MAX_SPEED_KMH;
        gc.setFill(Color.CYAN);
        gc.setFont(Font.font("Arial", FontWeight.BOLD, 16));
        gc.fillText((int) speedKmh + " Km/h", cx - 30, cy + 25);
        gc.save();
        gc.translate(cx, cy);
        gc.rotate(-180 + (180 * (Math.abs(currentSpeed) / MAX_SPEED)));
        gc.setStroke(Color.RED);
        gc.setLineWidth(3);
        gc.strokeLine(10, 0, r - 12, 0);
        gc.restore();
    }

    public static void main(String[] args) {
        launch(args);
    }
}