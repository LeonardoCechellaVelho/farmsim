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

    private Image imgPlowed, imgRoad, imgRoadBorder;
    private Image[] grassVariants = new Image[64];
    private Image[] darkGrassVariants = new Image[64];

    private int[][] noiseMap = new int[MAP_SIZE][MAP_SIZE];
    private javafx.scene.image.WritableImage miniMapImage = new javafx.scene.image.WritableImage(MAP_SIZE, MAP_SIZE);
    private int lastPlowCol = -1, lastPlowRow = -1;
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
        createTileCache();

        for (int r = 0; r < MAP_SIZE; r++) {
            for (int c = 0; c < MAP_SIZE; c++) {
                noiseMap[r][c] = (int) (getNoise(r, c) * 63);
                miniMapImage.getPixelWriter().setColor(c, r, Color.TRANSPARENT);
            }
        }

        try {
            tractorSheet = new Image(getClass().getResourceAsStream("/trator.png"));
        } catch (Exception e) {
            System.err.println("Erro: 'trator.png' não encontrado.");
        }

        Canvas canvas = new Canvas(WIDTH, HEIGHT);
        GraphicsContext gc = canvas.getGraphicsContext2D();
        StackPane root = new StackPane(canvas);
        Scene scene = new Scene(root);

        scene.setOnKeyPressed(e -> {
            activeKeys.add(e.getCode());
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

        stage.setTitle("Farm Simulator - Estilos Restaurados");
        stage.setScene(scene);
        stage.show();
    }

    private void createTileCache() {
        imgRoadBorder = createSingleTileImage(Color.web("#808080"), 0, false, null);
        imgRoad = createSingleTileImage(Color.web("#2c2c2c"), 0, false, null);
        imgPlowed = createSingleTileImage(Color.web("#3d2611"), 0, false, null);

        Color grassColor = Color.web("#2d4c21");
        Color bladeColor = Color.web("#3a5f27");
        Color darkGrassColor = Color.web("#1a2b13");
        Color darkBladeColor = Color.web("#233a1a");

        for (int i = 0; i < 64; i++) {
            grassVariants[i] = createSingleTileImage(grassColor, i, true, bladeColor);
            darkGrassVariants[i] = createSingleTileImage(darkGrassColor, i, true, darkBladeColor);
        }
    }

    private Image createSingleTileImage(Color baseColor, int seed, boolean hasGrass, Color bColor) {
        int w = TILE_SIZE * 2;
        int h = TILE_SIZE + 15;
        Canvas temp = new Canvas(w, h);
        GraphicsContext tgc = temp.getGraphicsContext2D();
        double off = 10;
        double[] xs = { TILE_SIZE, TILE_SIZE * 2, TILE_SIZE, 0 };
        double[] ys = { off, TILE_SIZE / 2.0 + off, TILE_SIZE + off, TILE_SIZE / 2.0 + off };
        tgc.setFill(baseColor);
        tgc.fillPolygon(xs, ys, 4);
        if (hasGrass && bColor != null) {
            java.util.Random rng = new java.util.Random(seed);
            int count = 6 + rng.nextInt(6);
            for (int i = 0; i < count; i++) {
                double rx = 4 + rng.nextDouble() * (TILE_SIZE * 1.5),
                        ry = off + 2 + rng.nextDouble() * (TILE_SIZE / 2.0);
                double gh = 3 + rng.nextDouble() * 5, bend = -2 + rng.nextDouble() * 4;
                tgc.setStroke(bColor.deriveColor(rng.nextDouble() * 10 - 5, 1, 0.8 + rng.nextDouble() * 0.4, 1));
                tgc.setLineWidth(1.0 + rng.nextDouble() * 0.5);
                tgc.strokeLine(rx, ry, rx + bend, ry - gh);
            }
        }
        javafx.scene.SnapshotParameters p = new javafx.scene.SnapshotParameters();
        p.setFill(Color.TRANSPARENT);
        return temp.snapshot(p, null);
    }

    private void toggleCouping() {
        if (isAttached)
            isAttached = false;
        else {
            double backX = tractorX - Math.cos(Math.toRadians(angle)) * 30;
            double backY = tractorY - Math.sin(Math.toRadians(angle)) * 30;
            if (Math.sqrt(Math.pow(backX - trailerX, 2) + Math.pow(backY - trailerY, 2)) < 35)
                isAttached = true;
        }
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
            double turn = Math.min(Math.abs(currentSpeed) * 1.2, BASE_ROTATION);
            if (activeKeys.contains(KeyCode.A))
                angle -= turn * dir;
            if (activeKeys.contains(KeyCode.D))
                angle += turn * dir;
        }

        double diff = angle - smoothedAngle;
        while (diff < -180)
            diff += 360;
        while (diff > 180)
            diff -= 360;
        smoothedAngle += diff * 0.25;

        double nextX = tractorX + Math.cos(Math.toRadians(angle)) * currentSpeed;
        double nextY = tractorY + Math.sin(Math.toRadians(angle)) * currentSpeed;
        double limit = (MAP_SIZE - 1) * TILE_SIZE;

        if (nextX >= 0 && nextX <= limit && nextY >= 0 && nextY <= limit) {
            tractorX = nextX;
            tractorY = nextY;
        } else {
            currentSpeed *= 0.5;
        }

        if (isAttached) {
            double dx = tractorX - trailerX, dy = tractorY - trailerY, dist = Math.sqrt(dx * dx + dy * dy);
            if (dist != TRAILER_DISTANCE) {
                double f = dist - TRAILER_DISTANCE;
                trailerX += Math.cos(Math.atan2(dy, dx)) * f;
                trailerY += Math.sin(Math.atan2(dy, dx)) * f;
                double adiff = Math.toDegrees(Math.atan2(dy, dx)) - trailerAngle;
                while (adiff < -180)
                    adiff += 360;
                while (adiff > 180)
                    adiff -= 360;
                if (Math.abs(currentSpeed) > 0.05)
                    trailerAngle += adiff * (currentSpeed > 0 ? 0.15 : 0.4);
            }

            int tx = (int) (trailerX / TILE_SIZE), ty = (int) (trailerY / TILE_SIZE);
            if (tx != lastPlowCol || ty != lastPlowRow) {
                lastPlowCol = tx;
                lastPlowRow = ty;
                for (int i = -3; i <= 3; i++) {
                    for (int j = -3; j <= 3; j++) {
                        int nx = tx + i, ny = ty + j;
                        if (ny >= 5 && ny < MAP_SIZE - 5 && nx >= 17 && nx < MAP_SIZE - 5) {
                            if (farmMap[ny][nx] == 0) {
                                farmMap[ny][nx] = 1;
                                miniMapImage.getPixelWriter().setColor(nx, ny, Color.web("#5d3a1a"));
                            }
                        }
                    }
                }
            }
        }
        cameraX = (tractorX - tractorY) - WIDTH / 2.0;
        cameraY = (tractorX + tractorY) / 2.0 - HEIGHT / 2.0;
    }

    private double getNoise(int x, int y) {
        int n = x * 45291 + y * 94607;
        n = (n << 13) ^ n;
        return (1.0 - ((n * (n * n * 15731 + 789221) + 1376312589) & 0x7fffffff) / 1073741824.0 + 1.0) / 2.0;
    }

    private void render(GraphicsContext gc) {
        gc.setFill(Color.web("#0d1a0a"));
        gc.fillRect(0, 0, WIDTH, HEIGHT);
        gc.save();
        gc.translate(-cameraX, -cameraY);

        int cCol = (int) (tractorX / TILE_SIZE), cRow = (int) (tractorY / TILE_SIZE), rad = 45;
        for (int r = Math.max(0, cRow - rad); r <= Math.min(MAP_SIZE - 1, cRow + rad); r++) {
            for (int c = Math.max(0, cCol - rad); c <= Math.min(MAP_SIZE - 1, cCol + rad); c++) {
                double ix = (c * TILE_SIZE - r * TILE_SIZE), iy = (c * TILE_SIZE + r * TILE_SIZE) / 2.0;
                if (ix > cameraX - 50 && ix < cameraX + WIDTH + 50 && iy > cameraY - 50 && iy < cameraY + HEIGHT + 50) {
                    Image img;
                    int margin = 5;
                    if (c < 12) {
                        if (c < 2 || c >= 10)
                            img = imgRoadBorder;
                        else
                            img = imgRoad;
                    } else if (c < 12 + margin || c >= MAP_SIZE - margin || r < margin || r >= MAP_SIZE - margin) {
                        img = darkGrassVariants[noiseMap[r][c]];
                    } else {
                        img = (farmMap[r][c] == 1) ? imgPlowed : grassVariants[noiseMap[r][c]];
                    }
                    gc.drawImage(img, Math.floor(ix - TILE_SIZE), Math.floor(iy - 10));
                }
            }
        }

        drawRoadLine(gc, 5.8, Color.web("#f1c40f"), 2);
        drawRoadLine(gc, 6.2, Color.web("#f1c40f"), 2);
        drawRoadLine(gc, 2.2, Color.WHITE, 1.5);

        if (isAttached) {
            double xt = (tractorX - tractorY), yt = (tractorX + tractorY) / 2.0 - 10;
            double fx = trailerX + Math.cos(Math.toRadians(trailerAngle)) * 5,
                    fy = trailerY + Math.sin(Math.toRadians(trailerAngle)) * 5;
            gc.setStroke(Color.BLACK);
            gc.setLineWidth(3);
            gc.strokeLine(xt, yt, (fx - fy), (fx + fy) / 2.0);
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

    private void drawRoadLine(GraphicsContext gc, double colPos, Color color, double width) {
        gc.setStroke(color);
        gc.setLineWidth(width);
        double xs = (colPos * TILE_SIZE), ys = (colPos * TILE_SIZE) / 2.0;
        double xe = (colPos * TILE_SIZE - MAP_SIZE * TILE_SIZE), ye = (colPos * TILE_SIZE + MAP_SIZE * TILE_SIZE) / 2.0;
        gc.strokeLine(xs, ys + TILE_SIZE / 2.0, xe, ye + TILE_SIZE / 2.0);
    }

    private void renderPlantadoraIso(GraphicsContext gc, double tx, double ty, double tA) {
        double ix = tx - ty, iy = (tx + ty) / 2.0;
        gc.save();
        gc.translate(ix, iy);
        gc.rotate(tA + 45);
        
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
        if (tractorSheet == null)
            return;
        double fa = (90 - ((smoothedAngle % 360 + 360) % 360) + 360) % 360;
        int idx = (int) Math.floor((fa + 7.5) / 15.0) % 24;
        double dw = SPRITE_W * TRACTOR_SCALE, dh = SPRITE_H * TRACTOR_SCALE;
        gc.save();
        gc.translate(x, y);
        gc.drawImage(tractorSheet, (idx % 6) * SPRITE_W, (idx / 6) * SPRITE_H, SPRITE_W, SPRITE_H, -dw / 2.0,
                -dh * 0.85, dw, dh);
        gc.restore();
    }

    private void renderMiniMap(GraphicsContext gc) {
        double sz = 140, mx = WIDTH - sz - 20, my = 20;
        gc.setFill(Color.web("#111111", 0.85));
        gc.fillOval(mx, my, sz, sz);
        gc.save();
        gc.beginPath();
        gc.arc(mx + sz / 2, my + sz / 2, sz / 2, sz / 2, 0, 360);
        gc.clip();
        double vr = 25;
        gc.drawImage(miniMapImage, (tractorX / TILE_SIZE) - vr, (tractorY / TILE_SIZE) - vr, vr * 2, vr * 2, mx, my, sz,
                sz);
        gc.setFill(Color.YELLOW);
        gc.fillOval(mx + sz / 2 - 3, my + sz / 2 - 3, 6, 6);
        gc.restore();
        gc.setStroke(Color.WHITE);
        gc.strokeOval(mx, my, sz, sz);
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
            gc.setFill(Color.GRAY);
            gc.setFont(Font.font("Arial", 10));
            gc.fillText(String.valueOf((int) ((i / 180.0) * MAX_SPEED_KMH)), cx + Math.cos(rad) * (r - 18) - 5,
                    cy - Math.sin(rad) * (r - 18) + 5);
        }
        double sk = (Math.abs(currentSpeed) / MAX_SPEED) * MAX_SPEED_KMH;
        gc.setFill(Color.CYAN);
        gc.setFont(Font.font("Arial", FontWeight.BOLD, 16));
        gc.fillText((int) sk + " Km/h", cx - 30, cy + 25);
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