import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.GridPane;
import javafx.scene.media.Media;
import javafx.scene.media.MediaPlayer;
import javafx.util.Duration;
import javafx.application.Platform;
import javafx.geometry.Insets;

public class PomodoroController {

    @FXML private Button focusButton;
    @FXML private Button shortBreakButton;
    @FXML private Button longBreakButton;
    @FXML private ProgressIndicator progressIndicator;
    @FXML private Label timerLabel;
    @FXML private Button startButton;
    @FXML private Button resetButton;
    @FXML private Label focusCountLabel;
    @FXML private Label shortBreakCountLabel;
    @FXML private Label longBreakCountLabel;
    @FXML private Button settingsButton;

    // Default durations
    private int focusTime = 25 * 60;
    private int shortBreakTime = 5 * 60;
    private int longBreakTime = 15 * 60;

    // Timer variables
    private Timeline timeline;
    private int totalSeconds = focusTime;
    private int remainingSeconds = totalSeconds;
    private boolean isRunning = false;
    private boolean isPaused = false;

    // Counters
    private int focusCount = 0;
    private int shortBreakCount = 0;
    private int longBreakCount = 0;

    // Session trackers
    private int focusSessionCount = 0;
    private boolean lastSessionWasFocus = true;
    private int unusedLongBreaks = 0;

    // Reset if paused for more than 2 minutes
    private Timeline pauseTimer;
    private static final int MAX_PAUSE_SECONDS = 120;
    private int pauseSecondsElapsed = 0;

    // Audio stuff
    private static final String SOUND_PATH = "/sounds/timerStops.mp3";
    private MediaPlayer mediaPlayer;

    @FXML
    public void initialize() {
        updateTimerDisplay();
        progressIndicator.setProgress(0.0);
        startButton.setText("Start");
        resetButton.setDisable(true);
        updateSessionLabels();

        startButton.setOnAction(e -> handleStartPauseResume());
        resetButton.setOnAction(e -> handleReset());

        focusButton.setOnAction(e -> {
            if (!isRunning && !isPaused) {
                switchMode(focusTime);
                lastSessionWasFocus = false;
            } else {
                showAlert("Cannot switch mode while timer is running or paused.");
            }
        });

        shortBreakButton.setOnAction(e -> {
            if (canStartShortBreak()) {
                switchMode(shortBreakTime);
                lastSessionWasFocus = false;
            } else {
                showAlert("Short break not allowed now. Finish a focus session first.");
            }
        });

        longBreakButton.setOnAction(e -> {
            if (canStartLongBreak()) {
                switchMode(longBreakTime);
                lastSessionWasFocus = false;
                if (focusSessionCount % 4 != 0 && unusedLongBreaks > 0) {
                    unusedLongBreaks = Math.max(0, unusedLongBreaks - 1);
                }
            } else {
                showAlert("Long break not allowed now. Finish four focus sessions or use unused long breaks.");
            }
        });

        settingsButton.setOnAction(e -> openSettingsDialog());
    }

    private boolean canStartShortBreak() {
        return lastSessionWasFocus;
    }

    private boolean canStartLongBreak() {
        return lastSessionWasFocus && (focusSessionCount % 4 == 0 || unusedLongBreaks > 0);
    }

    private void switchMode(int newModeSeconds) {
        if (timeline != null) {
            timeline.stop();
        }
        stopPauseTimer();

        totalSeconds = newModeSeconds;
        remainingSeconds = totalSeconds;
        isRunning = false;
        isPaused = false;
        updateTimerDisplay();
        progressIndicator.setProgress(0.0);
        startButton.setText("Start");
        resetButton.setDisable(true);
    }

    private void handleStartPauseResume() {
        if (!isRunning && !isPaused) {
            startTimer();
            isRunning = true;
            isPaused = false;
            startButton.setText("Pause");
            resetButton.setDisable(false);
            stopPauseTimer();
        } else if (isRunning) {
            pauseTimer();
            isRunning = false;
            isPaused = true;
            startButton.setText("Resume");
            startPauseAutoResetTimer();
        } else if (isPaused) {
            startTimer();
            isRunning = true;
            isPaused = false;
            startButton.setText("Pause");
            stopPauseTimer();
        }
    }

    private void startTimer() {
        if (timeline != null) {
            timeline.stop();
        }

        if (mediaPlayer != null) {
            mediaPlayer.stop();
            mediaPlayer.dispose();
            mediaPlayer = null;
        }

        timeline = new Timeline(new KeyFrame(Duration.seconds(1), e -> {
            remainingSeconds--;
            updateTimerDisplay();

            double progress = (double)(totalSeconds - remainingSeconds) / totalSeconds;
            progressIndicator.setProgress(progress);

            if (remainingSeconds <= 0) {
                timeline.stop();
                resetButton.setDisable(true);
                isRunning = false;
                isPaused = false;

                playSound();

                if (totalSeconds == focusTime) {
                    focusSessionCount++;
                    focusCount++;
                    lastSessionWasFocus = true;
                    updateSessionLabels();

                    if (focusSessionCount % 4 == 0) {
                        unusedLongBreaks++;
                        switchMode(longBreakTime);
                    } else {
                        switchMode(shortBreakTime);
                    }
                } else if (totalSeconds == shortBreakTime) {
                    shortBreakCount++;
                    lastSessionWasFocus = false;
                    updateSessionLabels();
                    switchMode(focusTime);
                } else if (totalSeconds == longBreakTime) {
                    longBreakCount++;
                    lastSessionWasFocus = false;
                    updateSessionLabels();
                    switchMode(focusTime);
                }

                startButton.setText("Start");
                remainingSeconds = totalSeconds;
                updateTimerDisplay();
                progressIndicator.setProgress(0.0);
            }
        }));

        timeline.setCycleCount(Timeline.INDEFINITE);
        timeline.play();
    }

    private void playSound() {
        try {
            if (mediaPlayer != null) {
                mediaPlayer.stop();
                mediaPlayer.dispose();
                mediaPlayer = null;
            }

            String path = getClass().getResource(SOUND_PATH).toExternalForm();
            Media sound = new Media(path);
            mediaPlayer = new MediaPlayer(sound);
            mediaPlayer.setOnEndOfMedia(() -> mediaPlayer.dispose());
            mediaPlayer.play();
        } catch (Exception e) {
            System.err.println("Failed to play alert sound: " + e.getMessage());
        }
    }

    private void pauseTimer() {
        if (timeline != null) {
            timeline.pause();
        }
    }

    private void handleReset() {
        if (timeline != null) {
            timeline.stop();
        }
        stopPauseTimer();
        remainingSeconds = totalSeconds;
        updateTimerDisplay();
        progressIndicator.setProgress(0.0);
        startButton.setText("Start");
        resetButton.setDisable(true);
        isRunning = false;
        isPaused = false;
    }

    private void updateTimerDisplay() {
        int minutes = remainingSeconds / 60;
        int seconds = remainingSeconds % 60;
        timerLabel.setText(String.format("%02d : %02d", minutes, seconds));
    }

    private void startPauseAutoResetTimer() {
        if (totalSeconds != focusTime) return;
        pauseSecondsElapsed = 0;

        pauseTimer = new Timeline(new KeyFrame(Duration.seconds(1), e -> {
            pauseSecondsElapsed++;
            if (pauseSecondsElapsed >= MAX_PAUSE_SECONDS) {
                handleReset();
                stopPauseTimer();

                Platform.runLater(() -> {
                    showAlert("Focus timer was paused for more than 2 minutes and has been reset automatically.");
                });
            }
        }));
        pauseTimer.setCycleCount(Timeline.INDEFINITE);
        pauseTimer.play();
    }

    private void stopPauseTimer() {
        if (pauseTimer != null) {
            pauseTimer.stop();
            pauseTimer = null;
        }
    }

    private void showAlert(String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("Pomodoro Timer");
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

    private void updateSessionLabels() {
        focusCountLabel.setText("Focus Sessions: " + focusCount);
        shortBreakCountLabel.setText("Short Breaks: " + shortBreakCount);
        longBreakCountLabel.setText("Long Breaks: " + longBreakCount);
    }

    // Settings

    private void openSettingsDialog() {
        Dialog<Void> dialog = new Dialog<>();
        dialog.setTitle("Settings");
        dialog.setHeaderText("Customize durations (minutes)");

        Label focusLabel = new Label("Focus Duration:");
        TextField focusInput = new TextField(String.valueOf(focusTime / 60));

        Label shortBreakLabel = new Label("Short Break Duration:");
        TextField shortBreakInput = new TextField(String.valueOf(shortBreakTime / 60));

        Label longBreakLabel = new Label("Long Break Duration:");
        TextField longBreakInput = new TextField(String.valueOf(longBreakTime / 60));

        // FXML stuff, will deal with these later

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(15);
        grid.setPadding(new Insets(20, 150, 10, 10));

        grid.add(focusLabel, 0, 0);
        grid.add(focusInput, 1, 0);
        grid.add(shortBreakLabel, 0, 1);
        grid.add(shortBreakInput, 1, 1);
        grid.add(longBreakLabel, 0, 2);
        grid.add(longBreakInput, 1, 2);

        dialog.getDialogPane().setContent(grid);

        ButtonType applyButtonType = new ButtonType("Apply", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(applyButtonType, ButtonType.CANCEL);

        // Validate inputs

        dialog.getDialogPane().lookupButton(applyButtonType).addEventFilter(javafx.event.ActionEvent.ACTION, event -> {
            try {
            int f = Integer.parseInt(focusInput.getText());
            int s = Integer.parseInt(shortBreakInput.getText());
            int l = Integer.parseInt(longBreakInput.getText());
            if (f < 1 || f > 99 || s < 1 || s > 99 || l < 1 || l > 99) {
                showAlert("All durations must be integers between 1 and 99.");
                event.consume();
            }
            } 
            catch (NumberFormatException ex) {
                showAlert("Please enter valid integer values.");
                event.consume();
            }
        });

        dialog.setResultConverter(dialogButton -> {
            if (dialogButton == applyButtonType) {
                int f, s, l;
                try {
                    f = Integer.parseInt(focusInput.getText());
                } catch (NumberFormatException e) {
                    f = 25; 
                }
                try {
                    s = Integer.parseInt(shortBreakInput.getText());
                } catch (NumberFormatException e) {
                    s = 5; 
                }
                try {
                    l = Integer.parseInt(longBreakInput.getText());
                } catch (NumberFormatException e) {
                    l = 15; 
                }
                // Set to default in case of invalid inputs
                focusTime = (f > 0) ? f * 60 : 25 * 60;
                shortBreakTime = (s > 0) ? s * 60 : 5 * 60;
                longBreakTime = (l > 0) ? l * 60 : 15 * 60;

            
                if (!isRunning && !isPaused) {
                    switchMode(focusTime);
                }
            }
            return null;
        });

        dialog.showAndWait();
    }
}
