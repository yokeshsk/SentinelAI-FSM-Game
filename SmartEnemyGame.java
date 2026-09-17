import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.util.HashSet;
import java.util.Set;

/**
 * SentinelAI: Intelligent Enemy Behavior System Using Finite State Machines
 *
 * A simple 2D game where an enemy character behaves intelligently:
 *  - PATROL : walks back and forth when it hasn't seen the player
 *  - CHASE  : moves toward the player once it spots them
 *  - ATTACK : stops and "attacks" when close enough
 *  - SEARCH : moves to the player's last known position, then gives up
 *
 * Controls: Arrow Keys / WASD to move the blue player square.
 * Watch the red enemy square change color and label as its state changes.
 *
 * NEW: Score + Health system
 *  - Score increases by 1 every second you survive.
 *  - Health starts at 100 and drops each time the enemy successfully attacks.
 *  - When health hits 0 -> Game Over screen. Press R to restart.
 */
public class SmartEnemyGame extends JPanel implements ActionListener {

    // ---------- Window / world settings ----------
    static final int WIDTH = 900;
    static final int HEIGHT = 600;

    // ---------- Player ----------
    double playerX = 100, playerY = 100;
    final int playerSize = 30;
    final double playerSpeed = 4.0;

    // ---------- Enemy ----------
    double enemyX = 700, enemyY = 400;
    final int enemySize = 34;
    final double enemySpeed = 2.3;
    final double chaseSpeedMultiplier = 1.6; // enemy moves faster while chasing

    // Patrol path (enemy walks back and forth between these two X points)
    double patrolLeftX = 600;
    double patrolRightX = 820;
    double patrolY = 400;
    boolean patrolMovingRight = true;

    // Detection / attack ranges (in pixels)
    final double detectionRange = 220;
    final double attackRange = 40;
    final double giveUpRange = 320; // if player gets this far while being chased, lose them

    // Last known player position (used during SEARCH state)
    double lastKnownX, lastKnownY;
    int searchTimer = 0;
    final int searchTimeLimit = 120; // ~2 seconds at 60fps before giving up search

    // Simple attack cooldown so it doesn't spam "ATTACK" text every frame
    int attackCooldown = 0;

    // ---------- NEW: Score & Health ----------
    int score = 0;
    int health = 100;
    final int attackDamage = 10;     // health lost per successful attack pulse
    long frameCounter = 0;           // used to turn frames into "seconds survived"
    boolean gameOver = false;

    // ---------- Finite State Machine ----------
    enum State { PATROL, CHASE, ATTACK, SEARCH }
    State currentState = State.PATROL;

    // ---------- Input handling ----------
    Set<Integer> pressedKeys = new HashSet<>();

    Timer gameTimer;

    public SmartEnemyGame() {
        setPreferredSize(new Dimension(WIDTH, HEIGHT));
        setBackground(new Color(30, 30, 35));
        setFocusable(true);

        addKeyListener(new KeyAdapter() {
            public void keyPressed(KeyEvent e) {
                pressedKeys.add(e.getKeyCode());
                // NEW: restart the game with R after Game Over
                if (e.getKeyCode() == KeyEvent.VK_R && gameOver) {
                    restartGame();
                }
            }
            public void keyReleased(KeyEvent e) {
                pressedKeys.remove(e.getKeyCode());
            }
        });

        // Game loop: runs every ~16ms (about 60 times per second)
        gameTimer = new Timer(16, this);
        gameTimer.start();
    }

    // Called automatically every timer tick (this IS the game loop)
    @Override
    public void actionPerformed(ActionEvent e) {
        if (!gameOver) {
            frameCounter++;
            handlePlayerMovement();
            updateEnemyStateMachine();
            moveEnemyAccordingToState();
            if (attackCooldown > 0) attackCooldown--;
            updateScore();
        }
        repaint();
    }

    // ---------------- NEW: SCORE ----------------
    private void updateScore() {
        // +1 point for every second survived (60 frames at ~60fps)
        if (frameCounter % 60 == 0) {
            score++;
        }
    }

    // ---------------- NEW: HEALTH / DAMAGE ----------------
    private void applyAttackDamage() {
        health -= attackDamage;
        if (health <= 0) {
            health = 0;
            gameOver = true;
        }
    }

    private void restartGame() {
        playerX = 100; playerY = 100;
        enemyX = 700; enemyY = 400;
        patrolMovingRight = true;
        currentState = State.PATROL;
        searchTimer = 0;
        attackCooldown = 0;
        score = 0;
        health = 100;
        frameCounter = 0;
        gameOver = false;
    }

    // ---------------- PLAYER MOVEMENT ----------------
    private void handlePlayerMovement() {
        if (pressedKeys.contains(KeyEvent.VK_LEFT) || pressedKeys.contains(KeyEvent.VK_A)) {
            playerX -= playerSpeed;
        }
        if (pressedKeys.contains(KeyEvent.VK_RIGHT) || pressedKeys.contains(KeyEvent.VK_D)) {
            playerX += playerSpeed;
        }
        if (pressedKeys.contains(KeyEvent.VK_UP) || pressedKeys.contains(KeyEvent.VK_W)) {
            playerY -= playerSpeed;
        }
        if (pressedKeys.contains(KeyEvent.VK_DOWN) || pressedKeys.contains(KeyEvent.VK_S)) {
            playerY += playerSpeed;
        }

        // Keep player inside the window
        playerX = clamp(playerX, 0, WIDTH - playerSize);
        playerY = clamp(playerY, 0, HEIGHT - playerSize);
    }

    // ---------------- THE CORE AI: FINITE STATE MACHINE ----------------
    private void updateEnemyStateMachine() {
        double distanceToPlayer = distance(enemyX, enemyY, playerX, playerY);

        switch (currentState) {

            case PATROL:
                // Switch to CHASE if the player enters detection range
                if (distanceToPlayer <= detectionRange) {
                    currentState = State.CHASE;
                    lastKnownX = playerX;
                    lastKnownY = playerY;
                }
                break;

            case CHASE:
                lastKnownX = playerX;
                lastKnownY = playerY;

                if (distanceToPlayer <= attackRange) {
                    currentState = State.ATTACK;
                } else if (distanceToPlayer > giveUpRange) {
                    // Player escaped too far -> go search their last known spot
                    currentState = State.SEARCH;
                    searchTimer = 0;
                }
                break;

            case ATTACK:
                // Stay in attack while close; go back to chase if player backs away
                if (distanceToPlayer > attackRange) {
                    currentState = State.CHASE;
                } else {
                    if (attackCooldown == 0) {
                        attackCooldown = 30; // roughly twice per second
                        applyAttackDamage(); // NEW: each attack pulse costs the player health
                    }
                }
                break;

            case SEARCH:
                searchTimer++;
                double distToLastKnown = distance(enemyX, enemyY, lastKnownX, lastKnownY);

                // If player comes back into range while searching, chase again
                if (distanceToPlayer <= detectionRange) {
                    currentState = State.CHASE;
                }
                // If enemy reached the last known spot (or ran out of patience), give up
                else if (distToLastKnown < 10 || searchTimer > searchTimeLimit) {
                    currentState = State.PATROL;
                }
                break;
        }
    }

    // ---------------- MOVEMENT LOGIC FOR EACH STATE ----------------
    private void moveEnemyAccordingToState() {
        switch (currentState) {

            case PATROL:
                if (patrolMovingRight) {
                    enemyX += enemySpeed;
                    if (enemyX >= patrolRightX) patrolMovingRight = false;
                } else {
                    enemyX -= enemySpeed;
                    if (enemyX <= patrolLeftX) patrolMovingRight = true;
                }
                enemyY = patrolY;
                break;

            case CHASE:
                moveToward(playerX, playerY, enemySpeed * chaseSpeedMultiplier);
                break;

            case ATTACK:
                // Enemy holds position while attacking
                break;

            case SEARCH:
                moveToward(lastKnownX, lastKnownY, enemySpeed);
                break;
        }
    }

    // Moves the enemy one step toward a target point at a given speed
    private void moveToward(double targetX, double targetY, double speed) {
        double dx = targetX - enemyX;
        double dy = targetY - enemyY;
        double dist = Math.sqrt(dx * dx + dy * dy);
        if (dist > 1) {
            enemyX += (dx / dist) * speed;
            enemyY += (dy / dist) * speed;
        }
    }

    private double distance(double x1, double y1, double x2, double y2) {
        return Math.sqrt(Math.pow(x2 - x1, 2) + Math.pow(y2 - y1, 2));
    }

    private double clamp(double val, double min, double max) {
        return Math.max(min, Math.min(max, val));
    }

    // ---------------- RENDERING ----------------
    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g;
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        if (gameOver) {
            drawGameOverScreen(g2);
            return;
        }

        // Draw patrol path guide
        g2.setColor(new Color(80, 80, 90));
        g2.drawLine((int) patrolLeftX + enemySize / 2, (int) patrolY + enemySize / 2,
                    (int) patrolRightX + enemySize / 2, (int) patrolY + enemySize / 2);

        // Draw detection range circle around enemy (visual aid)
        g2.setColor(new Color(255, 255, 255, 25));
        int detD = (int) detectionRange * 2;
        g2.fillOval((int) (enemyX + enemySize / 2 - detectionRange),
                    (int) (enemyY + enemySize / 2 - detectionRange), detD, detD);

        // Draw player
        g2.setColor(new Color(70, 140, 255));
        g2.fillRoundRect((int) playerX, (int) playerY, playerSize, playerSize, 8, 8);

        // Draw enemy (color changes based on state)
        g2.setColor(colorForState(currentState));
        g2.fillRoundRect((int) enemyX, (int) enemyY, enemySize, enemySize, 8, 8);

        // Draw state label above enemy
        g2.setColor(Color.WHITE);
        g2.setFont(new Font("Arial", Font.BOLD, 16));
        g2.drawString(currentState.toString(), (int) enemyX - 10, (int) enemyY - 12);

        // Attack flash effect
        if (currentState == State.ATTACK && attackCooldown > 20) {
            g2.setColor(new Color(255, 0, 0, 120));
            g2.fillOval((int) enemyX - 10, (int) enemyY - 10, enemySize + 20, enemySize + 20);
        }

        // HUD text
        g2.setColor(Color.LIGHT_GRAY);
        g2.setFont(new Font("Arial", Font.PLAIN, 14));
        g2.drawString("Move: Arrow Keys / WASD", 15, 25);
        g2.drawString("Current Enemy State: " + currentState, 15, 45);
        g2.drawString("Distance to enemy: " + (int) distance(enemyX, enemyY, playerX, playerY) + " px", 15, 65);

        // NEW: Score
        g2.setColor(Color.WHITE);
        g2.setFont(new Font("Arial", Font.BOLD, 20));
        g2.drawString("Score: " + score, WIDTH - 150, 30);

        // NEW: Health bar
        int barX = WIDTH - 220, barY = 45, barW = 200, barH = 18;
        g2.setColor(Color.GRAY);
        g2.fillRect(barX, barY, barW, barH);
        g2.setColor(health > 40 ? new Color(90, 200, 90) : new Color(220, 30, 30));
        g2.fillRect(barX, barY, (int) (barW * (health / 100.0)), barH);
        g2.setColor(Color.WHITE);
        g2.drawRect(barX, barY, barW, barH);
        g2.setFont(new Font("Arial", Font.PLAIN, 13));
        g2.drawString("Health: " + health, barX + 5, barY + 14);
    }

    // NEW: Game Over screen
    private void drawGameOverScreen(Graphics2D g2) {
        g2.setColor(Color.BLACK);
        g2.fillRect(0, 0, WIDTH, HEIGHT);

        g2.setColor(new Color(220, 30, 30));
        g2.setFont(new Font("Arial", Font.BOLD, 48));
        String title = "GAME OVER";
        FontMetrics fm = g2.getFontMetrics();
        g2.drawString(title, (WIDTH - fm.stringWidth(title)) / 2, HEIGHT / 2 - 40);

        g2.setColor(Color.WHITE);
        g2.setFont(new Font("Arial", Font.PLAIN, 24));
        String scoreMsg = "Final Score: " + score;
        fm = g2.getFontMetrics();
        g2.drawString(scoreMsg, (WIDTH - fm.stringWidth(scoreMsg)) / 2, HEIGHT / 2 + 10);

        String restartMsg = "Press R to Restart";
        fm = g2.getFontMetrics();
        g2.drawString(restartMsg, (WIDTH - fm.stringWidth(restartMsg)) / 2, HEIGHT / 2 + 50);
    }

    private Color colorForState(State s) {
        switch (s) {
            case PATROL: return new Color(90, 200, 90);   // green = calm
            case CHASE:  return new Color(255, 165, 0);   // orange = alert
            case ATTACK: return new Color(220, 30, 30);   // red = danger
            case SEARCH: return new Color(230, 220, 60);  // yellow = searching
            default: return Color.GRAY;
        }
    }

    // ---------------- MAIN METHOD ----------------
    public static void main(String[] args) {
        JFrame frame = new JFrame("SentinelAI - Smart Enemy Behavior System (FSM Demo)");
        SmartEnemyGame game = new SmartEnemyGame();
        frame.add(game);
        frame.pack();
        frame.setResizable(false);
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);
        game.requestFocusInWindow();
    }
}
