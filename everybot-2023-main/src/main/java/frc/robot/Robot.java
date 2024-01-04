// Copyright (c) FIRST and other WPILib contributors.
// Open Source Software; you can modify and/or share it under the terms of
// the WPILib BSD license file in the root directory of this project.

package frc.robot;


import com.ctre.phoenix.motorcontrol.ControlMode;
import com.ctre.phoenix.motorcontrol.NeutralMode;
import com.ctre.phoenix.motorcontrol.can.VictorSPX;
import com.revrobotics.CANSparkMax;
import com.revrobotics.CANSparkMax.IdleMode;
import com.revrobotics.CANSparkMaxLowLevel.MotorType;

import edu.wpi.first.wpilibj.TimedRobot;
import edu.wpi.first.wpilibj.Timer;
import edu.wpi.first.wpilibj.XboxController;
import edu.wpi.first.wpilibj.smartdashboard.SendableChooser;
import edu.wpi.first.wpilibj.smartdashboard.SmartDashboard;

import edu.wpi.first.networktables.NetworkTable;
import edu.wpi.first.networktables.NetworkTableEntry;
import edu.wpi.first.networktables.NetworkTableInstance;

import edu.wpi.first.cameraserver.CameraServer;
import edu.wpi.first.math.controller.PIDController;

import com.kauailabs.navx.frc.AHRS; //NAVX
import edu.wpi.first.wpilibj.SPI; //NAVX

public class Robot extends TimedRobot {
// Define the NavX MXP object
private AHRS navx;
private Timer timer = new Timer();

  /*
   * Autonomous selection options.
   */
  private static final String kNothingAuto = "do nothing";
  private static final String kConeAuto = "cone";
  private static final String kCubeAuto = "cube";
  //public static double powerpercentchoice = 35;
  private String m_autoSelected;
  private final SendableChooser<String> m_chooser = new SendableChooser<>();

  boolean timerStarted = false;

  /*
   * Drive motor controller instances.
   * 
   * Change the id's to match your robot.
   * Change kBrushed to kBrushless if you are using NEO's.
   * Use the appropriate other class if you are using different controllers.
   */
  CANSparkMax driveLeftSpark = new CANSparkMax(1, MotorType.kBrushed);
  CANSparkMax driveRightSpark = new CANSparkMax(2, MotorType.kBrushed);
  VictorSPX driveLeftVictor = new VictorSPX(3);
  VictorSPX driveRightVictor = new VictorSPX(4);

  
  /*
   * Mechanism motor controller instances.
   * 
   * Like the drive motors, set the CAN id's to match your robot or use different
   * motor controller classses (TalonFX, TalonSRX, Spark, VictorSP) to match your
   * robot.
   * 
   * The arm is a NEO on Everybud.
   * The intake is a NEO 550 on Everybud.
   */
  CANSparkMax arm = new CANSparkMax(5, MotorType.kBrushless);
  CANSparkMax intake = new CANSparkMax(6, MotorType.kBrushless);

  /**
   * The starter code uses the most generic joystick class.
   * 
   * The reveal video was filmed using a logitech gamepad set to
   * directinput mode (switch set to D on the bottom). You may want
   * to use the XBoxController class with the gamepad set to XInput
   * mode (switch set to X on the bottom) or a different controller
   * that you feel is more comfortable.
   */
  XboxController j = new XboxController(0);
  XboxController x = new XboxController(1);

  /*
   * Magic numbers. Use these to adjust settings.
   */

  /**
   * How many amps the arm motor can use.
   */
  static final int ARM_CURRENT_LIMIT_A = 20;

  /**
   * Percent output to run the arm up/down at
   */
  static final double ARM_OUTPUT_POWER = 0.3;

  /**
   * How many amps the intake can use while picking up
   */
  static final int INTAKE_CURRENT_LIMIT_A = 25;

  /**
   * How many amps the intake can use while holding
   */
  static final int INTAKE_HOLD_CURRENT_LIMIT_A = 5;

  /**
   * Percent output for intaking
   */
  static final double INTAKE_OUTPUT_POWER = .75;

  /**
   * Percent output for holding
   */
  static final double INTAKE_HOLD_POWER = 0.07;

  /**
   * Time to extend or retract arm in auto
   */
  static final double ARM_EXTEND_TIME_S = 2.0;

  /**
   * Time to throw game piece in auto
   */
  static final double AUTO_THROW_TIME_S = 0.375;

  /**
   * Time to drive back in auto
   */
  static final double AUTO_DRIVE_TIME = 3.4;

    /**
   * Time to drive forward in auto
   */
  static final double AUTO_DRIVE_TIME_FORWARD = 10.0;

  /**
   * Speed to drive backwards in auto
   */
  static final double AUTO_DRIVE_SPEED = -0.40;

    /**
   * Speed to drive backwards in auto
   */
  static final double AUTO_DRIVE_SPEED_FORWARD = 0.40;
  static public boolean ramp = false;
  static public boolean finalstop = false;
  /**
   * This method is run once when the robot is first started up.
   */
  @Override
  public void robotInit() {

     // Initialize the NavX MXP object on the SPI port
     navx = new AHRS(SPI.Port.kMXP);

    m_chooser.setDefaultOption("cone and mobility", kConeAuto);
    m_chooser.addOption("cone and mobility", kConeAuto);
    m_chooser.addOption("cube and mobility", kCubeAuto);
    SmartDashboard.putData("Auto choices", m_chooser);
    //Camera
    CameraServer.startAutomaticCapture();

    /*
     * You will need to change some of these from false to true.
     * 
     * In the setDriveMotors method, comment out all but 1 of the 4 calls
     * to the set() methods. Push the joystick forward. Reverse the motor
     * if it is going the wrong way. Repeat for the other 3 motors.
     */
    driveLeftSpark.setInverted(true);
    driveLeftVictor.setInverted(false);
    driveRightSpark.setInverted(false);
    driveRightVictor.setInverted(true);

    /*
     * Set the arm and intake to brake mode to help hold position.
     * If either one is reversed, change that here too. Arm out is defined
     * as positive, arm in is negative.
     */
    arm.setInverted(true);
    arm.setIdleMode(IdleMode.kBrake);
    arm.setSmartCurrentLimit(ARM_CURRENT_LIMIT_A);
    intake.setInverted(false);
    intake.setIdleMode(IdleMode.kBrake);
  }

  /**
   * Calculate and set the power to apply to the left and right
   * drive motors.
   * 
   * @param forward Desired forward speed. Positive is forward.
   * @param turn    Desired turning speed. Positive is counter clockwise from
   *                above.
   */
  public void setDriveMotors(double forward, double turn) {
    SmartDashboard.putNumber("drive forward power (%)", forward);
    SmartDashboard.putNumber("drive turn power (%)", turn);

    /*
     * positive turn = counter clockwise, so the left side goes backwards
     */
    double left = forward - turn;
    double right = forward + turn;

    SmartDashboard.putNumber("drive left power (%)", left);
    SmartDashboard.putNumber("drive right power (%)", right);

    // see note above in robotInit about commenting these out one by one to set
    // directions.
    driveLeftSpark.set(left);
    driveLeftVictor.set(ControlMode.PercentOutput, left);
    driveRightSpark.set(right);
    driveRightVictor.set(ControlMode.PercentOutput, right);
  }


  /**
   * Set the arm output power. Positive is out, negative is in.
   * 
   * @param percent
   */
  public void setArmMotor(double percent) {
    arm.set(percent);
    SmartDashboard.putNumber("arm power (%)", percent);
    SmartDashboard.putNumber("arm motor current (amps)", arm.getOutputCurrent());
    SmartDashboard.putNumber("arm motor temperature (C)", arm.getMotorTemperature());
  }

  /**
   * Set the arm output power.
   * 
   * @param percent desired speed
   * @param amps current limit
   */
  public void setIntakeMotor(double percent, int amps) {
    intake.set(percent);
    intake.setSmartCurrentLimit(amps);
    SmartDashboard.putNumber("intake power (%)", percent);
    SmartDashboard.putNumber("intake motor current (amps)", intake.getOutputCurrent());
    SmartDashboard.putNumber("intake motor temperature (C)", intake.getMotorTemperature());
  }

  /**
   * This method is called every 20 ms, no matter the mode. It runs after
   * the autonomous and teleop specific period methods.
   */
  @Override
  public void robotPeriodic() {
    SmartDashboard.putNumber("Time (seconds)", Timer.getFPGATimestamp());
          //**** START LimeLight Dashboard *****
          NetworkTable table = NetworkTableInstance.getDefault().getTable("limelight");
  
          NetworkTableEntry tx = table.getEntry("tx");
          NetworkTableEntry ty = table.getEntry("ty");
          NetworkTableEntry ta = table.getEntry("ta");
          
          //read values periodically
          double x = tx.getDouble(0.0);
          double y = ty.getDouble(0.0);
          double area = ta.getDouble(0.0);
      
          //post to smart dashboard periodically
          SmartDashboard.putNumber("LimelightX", x);
          SmartDashboard.putNumber("LimelightY", y);
          SmartDashboard.putNumber("LimelightArea", area);
    
          //***** END LimeLight Dashboard ****
  }

  double autonomousStartTime;
  double autonomousIntakePower;

  @Override
  public void autonomousInit() {
    driveLeftSpark.setIdleMode(IdleMode.kBrake);
    driveLeftVictor.setNeutralMode(NeutralMode.Brake);
    driveRightSpark.setIdleMode(IdleMode.kBrake);
    driveRightVictor.setNeutralMode(NeutralMode.Brake);

    m_autoSelected = m_chooser.getSelected();
    System.out.println("Auto selected: " + m_autoSelected);

    if (m_autoSelected == kConeAuto) {
      autonomousIntakePower = INTAKE_OUTPUT_POWER;
    } else if (m_autoSelected == kCubeAuto) {
      autonomousIntakePower = -INTAKE_OUTPUT_POWER;
    }

    autonomousStartTime = Timer.getFPGATimestamp();
     
    // ramp=false;
    // finalstop=false;
  }

  @Override
  public void autonomousPeriodic() {
    // Get the current yaw angle
    double yaw = navx.getYaw();
    // Get the current pitch angle
    double pitch = navx.getPitch();
    // Get the current roll angle
    double roll = navx.getRoll();

    // Post the yaw, pitch, and roll angles to the SmartDashboard
    SmartDashboard.putNumber("Navx Yaw Angle", yaw);
    SmartDashboard.putNumber("Navx Pitch Angle", pitch);
    SmartDashboard.putNumber("Navx Roll Angle", roll);
    if (m_autoSelected == kNothingAuto) {
      setArmMotor(0.0);
      setIntakeMotor(0.0, INTAKE_CURRENT_LIMIT_A);
      setDriveMotors(0.0, 0.0);
      return;
    }

  

    double timeElapsed = Timer.getFPGATimestamp() - autonomousStartTime;

    // if (timeElapsed < ARM_EXTEND_TIME_S) {
    //   setArmMotor(ARM_OUTPUT_POWER);
    //   setIntakeMotor(0.0, INTAKE_CURRENT_LIMIT_A);
    //   setDriveMotors(0.0, 0.0);
    // } else if (timeElapsed < ARM_EXTEND_TIME_S + AUTO_THROW_TIME_S) {
    //   setArmMotor(0.0);
    //   setIntakeMotor(autonomousIntakePower, INTAKE_CURRENT_LIMIT_A);
    //   setDriveMotors(0.0, 0.0);
    // } else if (timeElapsed < ARM_EXTEND_TIME_S + AUTO_THROW_TIME_S + ARM_EXTEND_TIME_S) {
    //   setArmMotor(-ARM_OUTPUT_POWER);
    //   setIntakeMotor(0.0, INTAKE_CURRENT_LIMIT_A);
    //   setDriveMotors(0.0, 0.0);
    // } else if (timeElapsed < ARM_EXTEND_TIME_S + AUTO_THROW_TIME_S + ARM_EXTEND_TIME_S + AUTO_DRIVE_TIME) {
    //   setArmMotor(0.0);
    //   setIntakeMotor(0.0, INTAKE_CURRENT_LIMIT_A);
    //     if (roll > 1 && roll < 3 && ramp==false) { // If front incline detected, set motors to slower speed
    //       setDriveMotors(0.30, 0);
    //       System.out.println("FORWARD Motor" + " " + roll + " " + ramp + " " + timeElapsed);
    //   } else if (roll < -2 && ramp==false) { // If rear incline detected, set motors to slower speed
    //     System.out.println("REAR Motor" + " " + roll + " " + ramp + " " + timeElapsed);
    //       setDriveMotors(-0.30, 0);
    //   } else if (roll <= -1 || ramp == true) { // If incline is too steep, brake the motors and exit the function
    //     ramp = true;
    //     //System.out.println("FINE TUNE AREA" + " " + roll + " " + ramp);
    //       if (roll > 0 && finalstop == false){ // THIS IS WHERE IT FINE TUNES ITSELF TO THE PLATFORM AFTER MEETING THE ROLL CONDITION
    //        // setDriveMotors(0.35, 0);
    //        System.out.println("FINE TUNE FORWARD MOTOR" + " " + roll + " " + ramp + " " + timeElapsed);
    //        setDriveMotors(.2, 0.0); // Stop the robot by setting the motor speed to 0
    //        driveLeftSpark.setIdleMode(IdleMode.kBrake);
    //        driveLeftVictor.setNeutralMode(NeutralMode.Brake);
    //        driveRightSpark.setIdleMode(IdleMode.kBrake);
    //        driveRightVictor.setNeutralMode(NeutralMode.Brake);
    //        return;
    //       } else if (roll <= -1 && roll > -15.7 && finalstop == false){
    //         System.out.println("FINE TUNE REAR MOTOR" + " " + roll + " " + ramp + " " + timeElapsed);
    //         setDriveMotors(-.35, 0);
    //       } else if(roll <= -15.5){
    //         finalstop = true;
    //         System.out.println("FINE TUNE STOP BACK FINAL" + " " + roll + " " + ramp + " " + timeElapsed);
    //         setDriveMotors(-.2, 0.0); // Stop the robot by setting the motor speed to 0
    //         driveLeftSpark.setIdleMode(IdleMode.kBrake);
    //         driveLeftVictor.setNeutralMode(NeutralMode.Brake);
    //         driveRightSpark.setIdleMode(IdleMode.kBrake);
    //         driveRightVictor.setNeutralMode(NeutralMode.Brake);
    //         return;
    //       } else if(roll >= 2){
    //           finalstop = true;
    //           System.out.println("FINE TUNE STOP FORWARD FINAL" + " " + roll + " " + ramp + " " + timeElapsed);
    //           setDriveMotors(.2, 0.0); // Stop the robot by setting the motor speed to 0
    //           driveLeftSpark.setIdleMode(IdleMode.kBrake);
    //           driveLeftVictor.setNeutralMode(NeutralMode.Brake);
    //           driveRightSpark.setIdleMode(IdleMode.kBrake);
    //           driveRightVictor.setNeutralMode(NeutralMode.Brake);
    //           return;
              
    //       }
         
    //   } else if(ramp==false){
    //     setDriveMotors(AUTO_DRIVE_SPEED, 0.0);
    //   }      
    // } else if(ramp==false)  {
    //   setArmMotor(0.0);
    //   setIntakeMotor(0.0, INTAKE_CURRENT_LIMIT_A);
    //   setDriveMotors(0.0, 0.0);
    // }

    if (timeElapsed < ARM_EXTEND_TIME_S) {
      setArmMotor(ARM_OUTPUT_POWER);
      setIntakeMotor(0.0, INTAKE_CURRENT_LIMIT_A);
      setDriveMotors(0.0, 0.0);
    } else if (timeElapsed < ARM_EXTEND_TIME_S + AUTO_THROW_TIME_S) {
      setArmMotor(0.0);
      setIntakeMotor(autonomousIntakePower, INTAKE_CURRENT_LIMIT_A);
      setDriveMotors(0.0, 0.0);
    } else if (timeElapsed < ARM_EXTEND_TIME_S + AUTO_THROW_TIME_S + ARM_EXTEND_TIME_S) {
      setArmMotor(-ARM_OUTPUT_POWER);
      setIntakeMotor(0.0, INTAKE_CURRENT_LIMIT_A);
      setDriveMotors(0.0, 0.0);
    } else if (timeElapsed < ARM_EXTEND_TIME_S + AUTO_THROW_TIME_S + ARM_EXTEND_TIME_S + AUTO_DRIVE_TIME) {
      setArmMotor(0.0);
      setIntakeMotor(0.0, INTAKE_CURRENT_LIMIT_A);
      setDriveMotors(AUTO_DRIVE_SPEED, 0.0);
      // if (timeElapsed >= ARM_EXTEND_TIME_S + AUTO_THROW_TIME_S + ARM_EXTEND_TIME_S + AUTO_DRIVE_TIME && roll > 1){
      //   setDriveMotors(0.20, 0);
      // } else if (roll < 0){
      //   setDriveMotors(-.20, 0);
  
      // }
    } else {
      setArmMotor(0.0);
      setIntakeMotor(0.0, INTAKE_CURRENT_LIMIT_A);
      setDriveMotors(0.0, 0.0);
    }
  }


 

  /**
   * Used to remember the last game piece picked up to apply some holding power.
   */
  static final int CONE = 1;
  static final int CUBE = 2;
  static final int NOTHING = 3;
  int lastGamePiece;

  @Override
  public void teleopInit() {
    driveLeftSpark.setIdleMode(IdleMode.kBrake);
    driveLeftVictor.setNeutralMode(NeutralMode.Brake);
    driveRightSpark.setIdleMode(IdleMode.kBrake);
    driveRightVictor.setNeutralMode(NeutralMode.Brake);

    lastGamePiece = NOTHING;


  }

  @Override
  public void teleopPeriodic() {
    // Get the current yaw angle
    double yaw = navx.getYaw();
    // Get the current pitch angle
    double pitch = navx.getPitch();
    // Get the current roll angle
    double roll = navx.getRoll();

    // Post the yaw, pitch, and roll angles to the SmartDashboard
    SmartDashboard.putNumber("Navx Yaw Angle", yaw);
    SmartDashboard.putNumber("Navx Pitch Angle", pitch);
    SmartDashboard.putNumber("Navx Roll Angle", roll);


    
     double armPower;
    // if (x.getRawButton(4)) { //ARM RAISES AND LOWERS WITH BUTTONS
    //   // lower the arm
    //   armPower = -ARM_OUTPUT_POWER;
    // } else if (x.getRawButton(2)) {
    //   // raise the arm
    //   armPower = ARM_OUTPUT_POWER;
    // } else {
    //   // do nothing and let it sit where it is
    //   armPower = 0.0;
    // }

    // if (x.getRawAxis(3) == 1) { //Hold down right trigger to raise arm
    //     // raise the arm
    //     armPower = ARM_OUTPUT_POWER;
    //   } else if(x.getRawAxis(3) == 0) { //Release right trigger to lower arm
    //      // lower the arm
    //     armPower = -ARM_OUTPUT_POWER;
    //   } else {
    //     // do nothing and let it sit where it is
    //     armPower = 0.0;
    //   }

    if (x.getRawAxis(3) == 1) { //Hold down right trigger to raise arm
      // raise the arm
      armPower = ARM_OUTPUT_POWER;
    } else if(x.getRawAxis(3) == 0 && x.getRawAxis(2) == 0) { //Release right trigger to stop arm movement
        // stop the arm movement
        armPower = 0.0;
    } else if (x.getRawAxis(2) == 1){
        // lower the arm
        armPower = -ARM_OUTPUT_POWER;
    } else {
        // do nothing and let it sit where it is
        armPower = 0.0;
    }

    setArmMotor(armPower);

    double intakePower;
    int intakeAmps;
    if (x.getRawButton(3)) { //LEFT TRIGGER x.getRawAxis(2)==1 //RIGHT TRIGGER x.getRawAxis(3)==1
      // cube in or cone out
      intakePower = INTAKE_OUTPUT_POWER;
      intakeAmps = INTAKE_CURRENT_LIMIT_A;
      lastGamePiece = CUBE;
    } else if (x.getRawButton(1)) {
      // cone in or cube out
      intakePower = -INTAKE_OUTPUT_POWER;
      intakeAmps = INTAKE_CURRENT_LIMIT_A;
      lastGamePiece = CONE;
    } else if (lastGamePiece == CUBE) {
      intakePower = INTAKE_HOLD_POWER;
      intakeAmps = INTAKE_HOLD_CURRENT_LIMIT_A;
    } else if (lastGamePiece == CONE) {
      intakePower = -INTAKE_HOLD_POWER;
      intakeAmps = INTAKE_HOLD_CURRENT_LIMIT_A;
    } else {
      intakePower = 0.0;
      intakeAmps = 0;
    }
    setIntakeMotor(intakePower, intakeAmps);

    /*
     * Negative signs here because the values from the analog sticks are backwards
     * from what we want. Forward returns a negative when we want it positive.
     */
   // setDriveMotors(-j.getRawAxis(1), -j.getRawAxis(4));

    //**** START LimeLight Dashboard *****
    NetworkTable table = NetworkTableInstance.getDefault().getTable("limelight");

    NetworkTableEntry tx = table.getEntry("tx");
    NetworkTableEntry ty = table.getEntry("ty");
    NetworkTableEntry ta = table.getEntry("ta");

    //read values periodically
    double x = tx.getDouble(0.0);
    double y = ty.getDouble(0.0);
    double area = ta.getDouble(0.0);

    //post to smart dashboard periodically
    SmartDashboard.putNumber("LimelightX", x);
    SmartDashboard.putNumber("LimelightY", y);
    SmartDashboard.putNumber("LimelightArea", area);

    //***** END LimeLight Dashboard ****
      //START OF PID CONTROLLER USING LIMELIGHT OFFSET
    // Initialize the PID controller for the x axis
    double kP = 0.05;
    double kI = 0.00;
    double kD = 0.00;
    PIDController pidX = new PIDController(kP, kI, kD);

    // Initialize the PID controller for the y axis
    kP = 0.02;
    kI = 0.00;
    kD = 0.00;
    PIDController pidY = new PIDController(kP, kI, kD);

    // Read the Limelight x and y values
    double xOffset = tx.getDouble(0.0);
    double yOffset = ty.getDouble(0.0);

    // Set the setpoints for the PID controller
    double targetX = 0.0;
    double targetY = 0.0;
    pidX.setSetpoint(targetX);
    pidY.setSetpoint(targetY);

    // Calculate the output of the PID controller
    double outputX = pidX.calculate(xOffset);
    double outputY = pidY.calculate(yOffset);

    // Use the output of the PID controller to control the movement of the robot
    // For example, you could use the output to set the speed of the motors
    //END OF PID CONTROLLER USING LIMELIGHT OFFSET
    SmartDashboard.putNumber("PID Output X", outputX);
    SmartDashboard.putNumber("PID Output Y", outputY);
    pidX.close();
    pidY.close();
    NetworkTableEntry tv = table.getEntry("tv");
    double targetDetected = tv.getDouble(0);

   if (j.getRawAxis(3)==1) { //Right trigger button held down to target
    // Automatic controller set with Limelight values if target detected
    if (targetDetected >= 1 && area < 1.5) { 
      // Center the target on tx=0
      double steer = -xOffset * kP;
      if (Math.abs(xOffset) < 10.0) { // if tx is within a tolerance range
        steer = 0.0; // center the target on tx = 0
      }
  
      // Use the steering value to turn the robot
      setDriveMotors(0.50, steer);
    } else {
      // Manual Xbox controller if no target detected or target already centered
      setDriveMotors(-j.getRawAxis(1), -j.getRawAxis(4));
    }
  } else if (j.getRawAxis(2)==1) { //Left trigger button held down to start autobalance
    if (roll > 1){
      setDriveMotors(0.20, 0);
    } else if (roll < 0){
      setDriveMotors(-.20, 0);

    }

  } else {
    //START SpecialMove
    if (j.getRawButtonPressed(1) && j.getPOV() == 180) { // A button and down D pad buttons together to make robot spin on its own
      // Start the timer when the button combo is pressed
      timer.reset();
      timer.start();
      setDriveMotors(0.0, .31);
      timerStarted = true;
    }
    else if (timer.get() >= 5.8) { // Stops motors when seconds has past
      // Stop motors when button is released and reset the timer
      setDriveMotors(0, 0);
      timer.reset();
      timer.stop();
      timerStarted = false;
    }
    else if(timerStarted==false || timer.get() == 0) {
      // Manual Xbox controller if button not pressed
      setDriveMotors(-j.getRawAxis(1), -j.getRawAxis(4));
    } 
    //END SpecialMove
  }

  }

  @Override
  public void testInit() {

    
  }
  @Override
  
  public void testPeriodic() {
   

    
    
  }
}
