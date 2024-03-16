import java.lang.reflect.Array;
import java.util.*;
import java.util.regex.*;

/**
 * See assignment handout for the grammar.
 * You need to implement the parse(..) method and all the rest of the parser.
 * There are several methods provided for you:
 * - several utility methods to help with the parsing
 * See also the TestParser class for testing your code.
 */
public class Parser {

    // Useful Patterns
    static final Pattern NUMPAT = Pattern.compile("-?[1-9][0-9]*|0"); 
    static final Pattern OPENPAREN = Pattern.compile("\\(");
    static final Pattern CLOSEPAREN = Pattern.compile("\\)");
    static final Pattern OPENBRACE = Pattern.compile("\\{");
    static final Pattern CLOSEBRACE = Pattern.compile("\\}");

    // Patterns for the categories
    static final Pattern ACT = Pattern.compile("move|turnL|turnR|takeFuel|wait|turnAround|shieldOn|shieldOff");
    static final Pattern RELOP = Pattern.compile("lt|gt|eq");
    static final Pattern SENS = Pattern.compile("fuelLeft|oppLR|oppFB|numBarrels|barrelLR|barrelFB|wallDist");

    // Syntax checking global vairables
    private Stack<String> bracketStack = new Stack<>();
    private static Map<String, String> oppositeBracket = Map.of(
        "{", "}",
        "}", "{",
        "(", ")",
        ")", "("
    );

    //----------------------------------------------------------------
    /**
     * The top of the parser, which is handed a scanner containing
     * the text of the program to parse.
     * Returns the parse tree.
     */
    ProgramNode parse(Scanner s) {
        // Set the delimiter for the scanner.
        s.useDelimiter("\\s+|(?=[{}(),;])|(?<=[{}(),;])");
        // THE PARSER GOES HERE
        // Call the parseProg method for the first grammar rule (PROG) and return the node

        // Build tree
        ProgramNode root = parseProg(s);

        // Check for uneven brackets
        if(!bracketStack.isEmpty()) { throw new ParserFailureException("Bracket number uneven"); }
        
        return root;
    }

    //----------------------------------------------------------------
    /**
     * Start of functions to parse the major blocks
     */

    /**
     * Parse the PROG statements
     */
    private ProgramNode parseProg(Scanner s){
        List<ProgramNode> children = new ArrayList<>();

        // Base case
        while (s.hasNext()) {
            children.add(parseStmt(s));
        }

        return new Prog(children);
    }

    /**
     * Parse the STMT statement
     */
    private ProgramNode parseStmt(Scanner s){
        if (s.hasNext(ACT)) { return(parseAct(s)); }
        else if (s.hasNext("loop")) { return(parseLoop(s)); }
        else if (s.hasNext("if")) { return(parseIf(s)); }
        else {fail("Expected STMT", s); return null;}
    }

    /**
     * Parse the ACT statements
     * Checks if scanner is empty and throw error if it is
     * Returns a different ACT ProgramNode depending on the 
     * next scanner string or throw error if it's undefined
     */
    private ProgramNode parseAct(Scanner s){
        // Base case
        if(!s.hasNext()) {return null;}

        // Error checking
        String action = s.next();
        require(";", "expecting: ;  At parseAct", s);
        

        // returns
        if (action.equals("move")) {
            return new Move();
        } 
        else if (action.equals("turnL")) {
            return new TurnL();
        } 
        else if (action.equals("turnR")) {
            return new TurnR();
        } 
        else if (action.equals("takeFuel")) {
            return new TakeFuel();
        } 
        else if (action.equals("wait")) {
            return new Wait();
        } 
        else if (action.equals("shieldOn")) {
            return new ShieldOn();
        }
        else if (action.equals("shieldOff")) {
            return new ShieldOff();
        }
        else if (action.equals("turnAround")) {
            return new TurnAround();
        }
        else {
            fail(String.format("expected ACTION node, got %s", action), s);
            return null;
        }
    }

    /**
     * Parse the LOOP statement
     * Returns a Loop ProgramNode
     */
    private ProgramNode parseLoop(Scanner s){
        // error checks
        require("loop", "expected: loop", s);

        // Get a liat of children nodes
        ArrayList<ProgramNode> childList = parseBlock(s);
        
        return new Loop(childList);
    }

    /**
     * Parse the BLOCK statement and return a list of ProgramNodes
     * As well as ckecking if the bracket syntax is correct
     * Returns an ArrayList of ProgramNodes
     */
    private ArrayList<ProgramNode> parseBlock(Scanner s){
        // Syntax checks
        addBracket(require(OPENBRACE, "Expected: {", s));

        // Store all child node of this loop
        ArrayList<ProgramNode> nodes = new ArrayList<>();

        // loop through all of the nodes and add them linearly
        while (!s.hasNext(CLOSEBRACE) && s.hasNext()){
            nodes.add(parseStmt(s));
        }

        // Syntax checks
        if(nodes.isEmpty()) { fail("BLOCK is empty", s);}
        removeBracket(require(CLOSEBRACE, "ecpected: }", s), s);

        return nodes;
    }

    /**
     * Parse the If statement
     * Retuens a If node
     */
    private ProgramNode parseIf(Scanner s){
        require("if", "expected if", s);
        BoolNode cond = parseCond(s);
        List<ProgramNode> childList = parseBlock(s);

        return new If(childList, cond);
    }


    /**
     * Parse the COND statement
     * returns a BoolNode
     */
    private BoolNode parseCond(Scanner s){
        // Check the syntax and get the RELOP, SENS and NUM strings
        require(OPENPAREN, "Conditions must start with (", s);
        String relop = require(RELOP, "Require a relop expression", s);
        require(OPENPAREN, "Conditions must start with (", s);
        String sens = require(SENS, relop, s);
        require(",", "Require ,", s);
        int num = requireInt(NUMPAT, sens, s);
        require(CLOSEPAREN, "Require )", s);
        require(CLOSEPAREN, "Require )", s);

        // Turn those strings into objects
        SensNode sensNode = parseSens(sens);
        BoolNode relopNode = parseRelop(relop, num, sensNode);

        return new Condition(relopNode);
    }

    /**
     * Parses and returns a BoolNode
     */
    private BoolNode parseRelop(String s, int i, SensNode sens){
        if (s.equals("lt")){
            return new LesserThan(sens, i);
        }
        if (s.equals("gt")){
            return new GreaterThan(sens, i);
        }
        if (s.equals("eq")){
            return new Equal(sens, i);
        }
        
        throw new IllegalArgumentException("RelopNode wrong: " + s);
    }

    /**
     * Parses and returns a sensNode
     */
    private SensNode parseSens(String s){
        if (s.equals("fuelLeft")) {
            return new FuelLeft();
        } 
        else if (s.equals("oppLR")) {
            return new OppLR();
        } 
        else if (s.equals("oppFB")) {
            return new OppFB();
        } 
        else if (s.equals("numBarrels")) {
            return new NumBarrels();
        } 
        else if (s.equals("barrelLR")) {
            return new BarrelLR();
        } 
        else if (s.equals("barrelFB")) {
            return new BarrelFB();
        }
        else if (s.equals("wallDist")) {
            return new WallDist();
        }

        throw new IllegalArgumentException("SensNode wrong");
    }

    //----------------------------------------------------------------
    /**
     * Helper functions for the main parser functions
     */

    /** 
     *Adds a bracket into bracketStack
    */
    private void addBracket(String bracket){ bracketStack.add(bracket); }

    /**
     * Checks if the closing bracket matches the opening bracket
     * If not, throw an exception
     * Else remove the matching bracket
     */
    private void removeBracket(String bracket, Scanner s){
        if (bracketStack.isEmpty()) { 
            fail("Too many closing brackets", null); 
        }
        else if (!bracketStack.peek().equals(oppositeBracket.get(bracket))){
            fail("Incorrect closing bracket", s);
        }
        else {
            bracketStack.pop();
        }
    }

    //----------------------------------------------------------------
    // utility methods for the parser
    // - fail(..) reports a failure and throws exception
    // - require(..) consumes and returns the next token as long as it matches the pattern
    // - requireInt(..) consumes and returns the next token as an int as long as it matches the pattern
    // - checkFor(..) peeks at the next token and only consumes it if it matches the pattern

    /**
     * Report a failure in the parser.
     */
    static void fail(String message, Scanner s) {
        String msg = message + "\n   @ ...";
        for (int i = 0; i < 5 && s.hasNext(); i++) {
            msg += " " + s.next();
        }
        throw new ParserFailureException(msg + "...");
    }

    /**
     * Requires that the next token matches a pattern if it matches, it consumes
     * and returns the token, if not, it throws an exception with an error
     * message
     */
    static String require(String p, String message, Scanner s) {
        if (s.hasNext(p)) {return s.next();}
        fail(message, s);
        return null;
    }

    static String require(Pattern p, String message, Scanner s) {
        if (s.hasNext(p)) {return s.next();}
        fail(message, s);
        return null;
    }

    /**
     * Requires that the next token matches a pattern (which should only match a
     * number) if it matches, it consumes and returns the token as an integer
     * if not, it throws an exception with an error message
     */
    static int requireInt(String p, String message, Scanner s) {
        if (s.hasNext(p) && s.hasNextInt()) {return s.nextInt();}
        fail(message, s);
        return -1;
    }

    static int requireInt(Pattern p, String message, Scanner s) {
        if (s.hasNext(p) && s.hasNextInt()) {return s.nextInt();}
        fail(message, s);
        return -1;
    }

    /**
     * Checks whether the next token in the scanner matches the specified
     * pattern, if so, consumes the token and return true. Otherwise returns
     * false without consuming anything.
     */
    static boolean checkFor(String p, Scanner s) {
        if (s.hasNext(p)) {s.next(); return true;}
        return false;
    }

    static boolean checkFor(Pattern p, Scanner s) {
        if (s.hasNext(p)) {s.next(); return true;} 
        return false;
    }

}

// You could add the node classes here or as separate java files.
// (if added here, they must not be declared public or private)
// For example:
//  class BlockNode implements ProgramNode {.....
//     with fields, a toString() method and an execute() method
//


/**
 * ================================================================
 * 
 *                          Interfaces
 */

/**
 * Interface for Boolean nodes
 * evaluate returns a boolean
 */
interface BoolNode {
    public boolean evaluate(Robot r);
}

/**
 * Interface for SENS nodes
 * evaluate returns an int
 */
interface SensNode {
    public int evaluate(Robot r);
}

/**
 * ================================================================
 * 
 *                          Classes
 */

 /**
  * The root of the AST
  */
class Prog implements ProgramNode{
    List<ProgramNode> children = new ArrayList<>();

    public Prog(List<ProgramNode> children){
        this.children = children;
    }

    @Override
    public void execute(Robot robot) {
        for (ProgramNode child : children){
            child.execute(robot);
        }
    }
    
    @Override
    public String toString(){
        return children.toString();
    }
}


/**
 * Move robot and if there's a next instructin, exicute it
 */
class Move implements ProgramNode{

    @Override
    public void execute(Robot robot) { robot.move(); }

    @Override
    public String toString(){
        return "move";
    }
}

/**
 * wait and if there's a next instructin, exicute it
 */
class Wait implements ProgramNode{

    @Override
    public void execute(Robot robot) { robot.idleWait(); }

    @Override
    public String toString(){
        return "Wait"; 
    }
}

/**
 * turn left and if there's a next instructin, exicute it
 */
class TurnL implements ProgramNode{

    @Override
    public void execute(Robot robot) { robot.turnLeft(); }

    @Override
    public String toString(){
        return "TurnL";
    }
}

/**
 * turn right and if there's a next instructin, exicute it
 */
class TurnR implements ProgramNode{

    @Override
    public void execute(Robot robot) { robot.turnRight(); }

    @Override
    public String toString(){
        return "TurnR";
    }
}

/**
 * take fuel and if there's a next instructin, exicute it
 */
class TakeFuel implements ProgramNode{

    @Override
    public void execute(Robot robot) { robot.takeFuel(); }

    @Override
    public String toString(){
        return "TakeFuel";
    }
}

/**
 * Turn around and if there's a next instructin, exicute it
 */
class TurnAround implements ProgramNode{

    @Override
    public void execute(Robot robot) { robot.turnAround(); }

    @Override
    public String toString(){
        return "TakeFuel";
    }
}

/**
 * Set shield to on and if there's a next instructin, exicute it
 */
class ShieldOn implements ProgramNode{

    @Override
    public void execute(Robot robot) { robot.setShield(true);}

    @Override
    public String toString(){
        return "TakeFuel";
    }
}

/**
 * Set shield to off and if there's a next instructin, exicute it
 */
class ShieldOff implements ProgramNode{

    @Override
    public void execute(Robot robot) { robot.setShield(false); }

    @Override
    public String toString(){
        return "TakeFuel";
    }
}

/**
 * Takes in a bool naode and a list of programNodes
 * If the bool node is ture then exicute all of the programNodes
 */
class If implements ProgramNode{
    List<ProgramNode> cNodes = new ArrayList<>();
    BoolNode bool;

    public If(List<ProgramNode> cNodes, BoolNode bool){
        this.cNodes = cNodes;
        this.bool = bool;
    }

    @Override
    public void execute(Robot robot) {
        if (bool.evaluate(robot)){
            for (ProgramNode child : cNodes){
                child.execute(robot);
            }
        }
    }

    @Override
    public String toString(){
        return "(if " + bool.toString() + cNodes.toString() + ")";
    }
}

/**
 * Takes in a bool naode and a list of programNodes
 * while the bool node is true, coontinue to exicute all of
 * the ProgramNodes
 */
class While implements ProgramNode{
    List<ProgramNode> cNodes = new ArrayList<>();
    BoolNode bool;

    public While(List<ProgramNode> cNodes, BoolNode bool){
        this.cNodes = cNodes;
        this.bool = bool;
    }

    @Override
    public void execute(Robot robot) {
        while (bool.evaluate(robot)){
            for (ProgramNode child : cNodes){
                child.execute(robot);
            }
        }
    }

    @Override
    public String toString(){
        return "(While " + bool.toString() + cNodes.toString() + ")";
    }
}

/**
 * Implements a loop that executes its children nodes in order
 * forever or until the condition has been met
 */
class Loop implements ProgramNode{
    List<ProgramNode> cNodes = new ArrayList<>();

    public Loop(List<ProgramNode> children){
        cNodes = children;
    }

    @Override
    public void execute(Robot robot) {
        while(true){
            for (ProgramNode child : cNodes){
                child.execute(robot);
            }
        }
    }

    @Override
    public String toString(){
        return "(Loop " + cNodes.toString() + ")";
    }
}

/**
 * This is the first level node that a COND block has, talks directly with ifs and whiles
 * Returns a boolean
 */
class Condition implements BoolNode{
    private BoolNode cNode;

    public Condition(BoolNode child){
        cNode = child;
    }

    @Override
    public boolean evaluate(Robot r) {
        return cNode.evaluate(r);
    }

    @Override
    public String toString(){
        if (cNode != null){return cNode.toString();}
        else {return "";}
    }
}

/**
 * This is one of the second level level node of the COND block
 * Returns true if both children are equal
 */
class Equal implements BoolNode {
    private SensNode sens;
    private int num;

    public Equal(SensNode sens, int num){
        this.sens = sens;
        this.num = num;
    }

    @Override
    public boolean evaluate(Robot r) {
        return sens.evaluate(r) == num;
    }

    @Override
    public String toString(){
        return String.format("(%s == %d)", sens.toString(), num);
    }
}

/**
 * This is one of the second level level node of the COND block
 * Returns true if child 1 is greater than child 2
 */
class GreaterThan implements BoolNode {
    private SensNode sens;
    private int num;

    public GreaterThan(SensNode sens, int num){
        this.sens = sens;
        this.num = num;
    }

    @Override
    public boolean evaluate(Robot r) {
        return sens.evaluate(r) > num;
    }

    @Override
    public String toString(){
        return String.format("(%s > %d)", sens.toString(), num);
    }
}

/**
 * This is one of the second level level node of the COND block
 * Returns true if child 1 is less than child 2
 */
class LesserThan implements BoolNode {
    private SensNode sens;
    private int num;

    public LesserThan(SensNode sens, int num){
        this.sens = sens;
        this.num = num;
    }

    @Override
    public boolean evaluate(Robot r) {
        return sens.evaluate(r) < num;
    }

    @Override
    public String toString(){
        return String.format("(%s > %d)", sens.toString(), num);
    }
}

/**
 * Returns the fuel level of the robot
 */
class FuelLeft implements SensNode{

    @Override
    public int evaluate(Robot r) {
        return r.getFuel();
    }

    @Override
    public String toString(){
        return "fuelLeft";
    }
}

/**
 * Returns the Opponents's Left or right position
 */
class OppLR implements SensNode{

    @Override
    public int evaluate(Robot r) {
        return r.getOpponentLR();
    }

    @Override
    public String toString(){
        return "OppLR";
    }
}

/**
 * Returns the opponent's fowards and backwards position
 */
class OppFB implements SensNode{

    @Override
    public int evaluate(Robot r) {
        return r.getOpponentFB();
    }

    @Override
    public String toString(){
        return "OppFB";
    }
}

/**
 * Returns the number of barrels on the board
 */
class NumBarrels implements SensNode{

    @Override
    public int evaluate(Robot r) {
        return r.numBarrels();
    }

    @Override
    public String toString(){
        return "numBarrels";
    }
}

/**
 * Returns the Left and right position of the closest barrel
 */
class BarrelLR implements SensNode{

    @Override
    public int evaluate(Robot r) {
        return r.getClosestBarrelLR();
    }

    @Override
    public String toString(){
        return "BarrelLR";
    }
}

/**
 * Returns the fowards backwards position of the closest barrel
 */
class BarrelFB implements SensNode{

    @Override
    public int evaluate(Robot r) {
        return r.getClosestBarrelFB();
    }

    @Override
    public String toString(){
        return "BarrelFB";
    }
}

/**
 * Returns the distance to the wall of the robot
 */
class WallDist implements SensNode{

    @Override
    public int evaluate(Robot r) {
        return r.getDistanceToWall();
    }

    @Override
    public String toString(){
        return "WallDist";
    }
}