import java.lang.reflect.Array;
import java.util.*;
import java.util.regex.*;

import javax.lang.model.element.VariableElement;
import javax.naming.InterruptedNamingException;

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
    static final Pattern VAIRABLE = Pattern.compile("\\$[A-Za-z][A-Za-z0-9]*");

    // Patterns for the bigger categories
    static final Pattern ACT = Pattern.compile("move|turnL|turnR|takeFuel|wait|turnAround|shieldOn|shieldOff");
    static final Pattern RELOP = Pattern.compile("lt|gt|eq");
    static final Pattern COND = Pattern.compile("and|or|not");
    static final Pattern SENS = Pattern.compile("fuelLeft|oppLR|oppFB|numBarrels|barrelLR|barrelFB|wallDist|$");
    static final Pattern OP = Pattern.compile("add|sub|mul|div");

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
        
        return parseProg(s);
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

        if (s.hasNext(ACT))                     { return(parseAct(s)); }
        else if (s.hasNext("loop"))     { return(parseLoop(s)); }
        else if (s.hasNext("if"))       { return(parseIf(s)); }
        else if (s.hasNext("while"))    { return(parseWhile(s)); }
        else if (s.hasNext(VAIRABLE))           { return(setVar(s));}
        else {
            fail("Expected STMT", s); 
            return null;
        }
    }

    /**
     * Parse the ACT statements
     * Checks if scanner is empty and throw error if it is
     * Returns a different ACT ProgramNode depending on the 
     * next scanner string or throw error if it's undefined
     */
    private ProgramNode parseAct(Scanner s){
        // Error checking
        String action = require(ACT, "expected act", s);
        
        // Node to Return
        ProgramNode act = null;

        // Act nodes without any extra parameters
        if      (action.equals("turnL"))        { act = new TurnL(); } 
        else if (action.equals("turnR"))        { act = new TurnR(); } 
        else if (action.equals("takeFuel"))     { act = new TakeFuel(); } 
        else if (action.equals("shieldOn"))     { act = new ShieldOn(); }
        else if (action.equals("shieldOff"))    { act = new ShieldOff(); }
        else if (action.equals("turnAround"))   { act = new TurnAround(); }

        // Act nodes with parameters
        if (action.equals("move")) { 
            IntNode repeat = null;
            if (s.hasNext(OPENPAREN)){
                s.next();
                repeat = parseExpr(s);
                require(CLOSEPAREN, "expected )", s);   
            } else {
                repeat = new Num(1);
            }
            act = new Move(repeat); 
        } 
        else if (action.equals("wait")){ 
            IntNode repeat = null;
            if (s.hasNext(OPENPAREN)){
                s.next();
                repeat = parseExpr(s);
                require(CLOSEPAREN, "expected )", s);   
            } else {
                repeat = new Num(1);
            }
            act = new Wait(repeat); 
        } 
        
        require(";", "expecting: ;  At parseAct", s);
        return act;
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
        require(OPENBRACE, "Expected: {", s);

        // Store all child node of this loop
        ArrayList<ProgramNode> nodes = new ArrayList<>();

        // loop through all of the nodes and add them linearly
        while (!s.hasNext(CLOSEBRACE) && s.hasNext()){
            nodes.add(parseStmt(s));
        }

        // Syntax checks
        if(nodes.isEmpty()) { fail("BLOCK is empty", s);}
        require(CLOSEBRACE, "ecpected: }", s);

        return nodes;
    }

    /**
     * Parse the If statement
     * Retuens a If node
     */
    private ProgramNode parseIf(Scanner s){
        //parsing if
        require("if", "expected if", s);
        require(OPENPAREN, "Conditions must start with (", s);
        BoolNode cond = parseCond(s);
        require(CLOSEPAREN, "Require )", s);
        List<ProgramNode> ifList = parseBlock(s);

        //parsing elif
        Map<BoolNode, List<ProgramNode>> elifMap = new HashMap<>();
        while (s.hasNext("elif")){
            require("elif", "expected elif", s);
            require(OPENPAREN, "Conditions must start with (", s);
            BoolNode elifCond = parseCond(s);
            require(CLOSEPAREN, "Require )", s);
            List<ProgramNode> elifList = parseBlock(s);
            elifMap.put(elifCond, elifList);
        }

        //parsing else
        List<ProgramNode> elseList = new ArrayList<>();
        if (s.hasNext("else")) {
            s.next();
            elseList = parseBlock(s);
        }

        return new If(ifList, elifMap, elseList, cond);
    }

    /**
     * Parse the If statement
     * Retuens a If node
     */
    private ProgramNode parseWhile(Scanner s){
        require("while", "expected while", s);
        require(OPENPAREN, "Conditions must start with (", s);
        BoolNode cond = parseCond(s);
        require(CLOSEPAREN, "Require )", s);

        List<ProgramNode> childList = parseBlock(s);

        return new While(childList, cond);
    }

    /**
     * Parse the outer COND statement
     * returns a BoolNode
     */
    private BoolNode parseCond(Scanner s){
        // Check the syntax and get the condition strings
        if (s.hasNext(RELOP)){
            return parseRelop(s);
        }
        else if (s.hasNext("and")){
            s.next();
            require(OPENPAREN, "expected (", s);
            BoolNode first = parseCond(s);
            require(",", "expected ,", s);
            BoolNode second = parseCond(s);
            require(CLOSEPAREN, "expected )", s);
            return new And(first, second);
        }
        else if (s.hasNext("or")){
            s.next();
            require(OPENPAREN, "expected (", s);
            BoolNode first = parseCond(s);
            require(",", "expected ,", s);
            BoolNode second = parseCond(s);
            require(CLOSEPAREN, "expected )", s);
            return new Or(first, second);
        }
        else if (s.hasNext("not")){
            s.next();
            require(OPENPAREN, "expected (", s);
            BoolNode first = parseCond(s);
            require(CLOSEPAREN, "expected )", s);
            return new Not(first);
        }

        fail("Cond statement incorrect", s);
        return null;
    }



    /**
     * Parses and returns a BoolNode
     */
    private BoolNode parseRelop(Scanner s){
        String relop = s.next();
        require(OPENPAREN, "Expected (", s);

        if (relop.equals("lt")){
            IntNode first = parseExpr(s);
            require(",", "Expected ,", s);
            IntNode second = parseExpr(s);
            require(CLOSEPAREN, "expected )", s);
            return new LesserThan(first, second);
        }
        else if (relop.equals("gt")){
            IntNode first = parseExpr(s);
            require(",", "Expected ,", s);
            IntNode second = parseExpr(s);
            require(CLOSEPAREN, "expected )", s);
            return new GreaterThan(first, second);
        }
        else if (relop.equals("eq")){
            IntNode first = parseExpr(s);
            require(",", "Expected ,", s);
            IntNode second = parseExpr(s);
            require(CLOSEPAREN, "expected )", s);
            return new Equal(first, second);
        }
        
        throw new ParserFailureException("RelopNode wrong, got: " + s.next());
    }

    /**
     * Parses the EXPR statement
     */
    private IntNode parseExpr(Scanner s){
        if (s.hasNextInt()){
            return new Num(s.nextInt());
        }
        else if (s.hasNext(SENS)){
            return parseSens(s);
        }
        else if (s.hasNext(VAIRABLE)){
            return parseVar(s);
        }
        else if(s.hasNext(OP)){
            String op = s.next();

            // Syntax checks and recursion
            require(OPENPAREN, "Expected (", s);
            IntNode first = parseExpr(s);
            require(",", "Expected ,", s);
            IntNode second = parseExpr(s);
            require(CLOSEPAREN, "expected )", s);

            // Find the right type of node
            if      (op.equals("add"))  { return new Add(first, second); }
            else if (op.equals("sub"))  { return new Subtract(first, second); }
            else if (op.equals("mul"))  { return new Mulitiply(first, second); }
            else if (op.equals("div"))  { return new Divide(first, second); }
        }

        throw new ParserFailureException("Expr wrong, got: " + s.next());
    }

    /**
     * Parses and returns a sensNode
     */
    private IntNode parseSens(Scanner s){
        String sensScanner = s.next();
        IntNode sens;

        // SENS nodes that doesn't take any parameters
        if (sensScanner.equals("fuelLeft"))           { sens = new FuelLeft(); } 
        else if (sensScanner.equals("oppLR"))         { sens = new OppLR(); } 
        else if (sensScanner.equals("oppFB"))         { sens = new OppFB(); } 
        else if (sensScanner.equals("numBarrels"))    { sens = new NumBarrels(); } 
        else if (sensScanner.equals("wallDist"))      { sens = new WallDist(); }
        else if (sensScanner.startsWith("$"))           { sens = parseVar(s); }

        // SENS nodes that takes in parameters
        else if (sensScanner.equals("barrelLR")){ 
            IntNode repeat = null;
            if (s.hasNext(OPENPAREN)){
                s.next();
                repeat = parseExpr(s);
                require(CLOSEPAREN, "expected )", s);   
            } else {
                repeat = new Num(Integer.MIN_VALUE);
            }
            return new BarrelLR(repeat); 
        } 
        else if (sensScanner.equals("barrelFB")){ 
            IntNode repeat = null;
            if (s.hasNext(OPENPAREN)){
                s.next();
                repeat = parseExpr(s);
                require(CLOSEPAREN, "expected )", s);   
            } else {
                repeat = new Num(Integer.MIN_VALUE);
            }
            return new BarrelFB(repeat); 
        }
        else { throw new ParserFailureException("Sens not right"); }

        return sens;
    }

    /**
     * returns an intNodethat can be used in the middle of an expression and throws an error when 
     * trying to define a new variable
     */
    public IntNode parseVar(Scanner s){

        IntNode toReturn;
        if (s.hasNext("=")) {
            fail("cannot assign variable in the middle of expression", s);
            throw new ParserFailureException(null);
        } else {
            toReturn = new useVariable(s.next());
        }

        return toReturn;
    }

    /**
     * returns a setVariable programNode
     */
    public ProgramNode setVar(Scanner s){
        String key = require(VAIRABLE, "Expected variable name", s);
        require("=", "require = when assigning variables", s);
        IntNode expr = parseExpr(s);
        require(";", "variable declaration must end with ;", s);

        return new SetVariable(key, expr);
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
interface IntNode {
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
    private IntNode repeat;

    public Move(IntNode repeat){
        this.repeat = repeat;
    }

    @Override
    public void execute(Robot robot) { 
        int rep = repeat.evaluate(robot);
        for (int i = 0; i < rep; i++){
            robot.move(); 
        }
    }

    @Override
    public String toString(){
        return "move";
    }
}

/**
 * wait and if there's a next instructin, exicute it
 */
class Wait implements ProgramNode{
    private IntNode repeat;

    public Wait(IntNode repeat){
        this.repeat = repeat;
    }

    @Override
    public void execute(Robot robot) { 
        int rep = repeat.evaluate(robot);
        for (int i = 0; i < rep; i++){
            robot.idleWait(); 
        }
    }

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
    List<ProgramNode> ifNodes = new ArrayList<>();
    List<ProgramNode> elseNodes = new ArrayList<>();
    Map<BoolNode, List<ProgramNode>> elifMap = new HashMap<>();
    BoolNode bool;

    //constructor
    public If(List<ProgramNode> ifNodes, Map<BoolNode, List<ProgramNode>> elifMap, List<ProgramNode> elseNodes, BoolNode bool){
        this.ifNodes = ifNodes;
        this.elifMap = elifMap;
        this.elseNodes = elseNodes;
        this.bool = bool;
    }

    @Override
    public void execute(Robot robot) {
        //check the if statement
        if (bool.evaluate(robot)){
            for (ProgramNode child : ifNodes){
                child.execute(robot);
            }
        }  
        else {
            //check the else if statements
            for (Map.Entry<BoolNode, List<ProgramNode>> map : elifMap.entrySet()){
                if (map.getKey().evaluate(robot)){
                    for (ProgramNode child : map.getValue()){
                        child.execute(robot);
                    } 
                }else {
                    //skips to the next else if statement to avoid returning and skipping the else
                    continue;
                }
                //returns and skip the else, if one of the conditions are true
                return;
            }

            //execute the else statement
            for (ProgramNode child : elseNodes){
                child.execute(robot);
            }
        }
    }

    @Override
    public String toString(){
        return "if " + bool.toString() + ifNodes.toString() + " else " + elseNodes.toString();
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
    private IntNode first;
    private IntNode second;

    public Equal(IntNode first, IntNode second){
        this.first = first;
        this.second = second;
    }

    @Override
    public boolean evaluate(Robot r) {
        return first.evaluate(r) == second.evaluate(r);
    }

    @Override
    public String toString(){
        return String.format("(%s == %s)", first.toString(), second.toString());
    }
}

/**
 * This is one of the second level level node of the COND block
 * Returns true if child 1 is greater than child 2
 */
class GreaterThan implements BoolNode {
    private IntNode first;
    private IntNode second;

    public GreaterThan(IntNode first, IntNode second){
        this.first = first;
        this.second = second;
    }

    @Override
    public boolean evaluate(Robot r) {
        return first.evaluate(r) > second.evaluate(r);
    }

    @Override
    public String toString(){
        return String.format("(%s > %s)", first.toString(), second.toString());
    }
}

/**
 * This is one of the second level level node of the COND block
 * Returns true if child 1 is less than child 2
 */
class LesserThan implements BoolNode {
    private IntNode first;
    private IntNode second;

    public LesserThan(IntNode first, IntNode second){
        this.first = first;
        this.second = second;
    }

    @Override
    public boolean evaluate(Robot r) {
        return first.evaluate(r) < second.evaluate(r);
    }

    @Override
    public String toString(){
        return String.format("(%s < %s)", first.toString(), second.toString());
    }
}

/**
 * Returns the fuel level of the robot
 */
class FuelLeft implements IntNode{

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
class OppLR implements IntNode{

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
class OppFB implements IntNode{

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
class NumBarrels implements IntNode{

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
class BarrelLR implements IntNode{
    private IntNode count;

    public BarrelLR(IntNode count){
        this.count = count;
    }

    @Override
    public int evaluate(Robot r) {
        int num = count.evaluate(r);
        if (num == Integer.MIN_VALUE){
            return r.getClosestBarrelLR();
        } else {
            return r.getBarrelLR(num);
        }
    }

    @Override
    public String toString(){
        return "BarrelLR";
    }
}

/**
 * Returns the fowards backwards position of the closest barrel
 */
class BarrelFB implements IntNode{
    private IntNode count;

    public BarrelFB(IntNode count){
        this.count = count;
    }

    @Override
    public int evaluate(Robot r) {
        int num = count.evaluate(r);
        if (num == Integer.MIN_VALUE){
            return r.getClosestBarrelFB();
        } else {
            return r.getBarrelFB(num);
        }
    }

    @Override
    public String toString(){
        return "BarrelFB";
    }
}

/**
 * Returns the distance to the wall of the robot
 */
class WallDist implements IntNode{

    @Override
    public int evaluate(Robot r) {
        return r.getDistanceToWall();
    }

    @Override
    public String toString(){
        return "WallDist";
    }
}

/**
 * Returns the a number
 */
class Num implements IntNode{
    private int num;

    public Num(int num){
        this.num = num;
    }

    @Override
    public int evaluate(Robot r) {
        return num;
    }

    @Override
    public String toString(){
        return "WallDist";
    }
}

/**
 * Add the 2 exressions given
 */
class Add implements IntNode{
    IntNode int1, int2;

    public Add(IntNode int1, IntNode int2){
        this.int1 = int1;
        this.int2 = int2;
    }

    @Override
    public int evaluate(Robot r) {
        return int1.evaluate(r) + int2.evaluate(r);
    }

    @Override
    public String toString(){
        return String.format("%s + %s", int1.toString(), int2.toString());
    }
}

/**
 * Subtract the 2 exressions given
 */
class Subtract implements IntNode{
    IntNode int1, int2;

    public Subtract(IntNode int1, IntNode int2){
        this.int1 = int1;
        this.int2 = int2;
    }

    @Override
    public int evaluate(Robot r) {
        return int1.evaluate(r) - int2.evaluate(r);
    }

    @Override
    public String toString(){
        return String.format("(%s - %s)", int1.toString(), int2.toString());
    }
}

/**
 * Mulitiply the 2 exressions given
 */
class Mulitiply implements IntNode{
    IntNode int1, int2;

    public Mulitiply(IntNode int1, IntNode int2){
        this.int1 = int1;
        this.int2 = int2;
    }

    @Override
    public int evaluate(Robot r) {
        return int1.evaluate(r) * int2.evaluate(r);
    }

    @Override
    public String toString(){
        return String.format("(%s * %s)", int1.toString(), int2.toString());
    }
}

/**
 * Divide the 2 exressions given
 */
class Divide implements IntNode{
    IntNode int1, int2;

    public Divide(IntNode int1, IntNode int2){
        this.int1 = int1;
        this.int2 = int2;
    }

    @Override
    public int evaluate(Robot r) {
        return int1.evaluate(r) / int2.evaluate(r);
    }

    @Override
    public String toString(){
        return String.format("(%s / %s)", int1.toString(), int2.toString());
    }
}

/**
 * represents the and boolean operation
 */
class And implements BoolNode {
    BoolNode first;
    BoolNode second;

    public And(BoolNode first, BoolNode second){
        this.first = first;
        this.second = second;
    }

    @Override
    public boolean evaluate(Robot r) {
        return first.evaluate(r) && second.evaluate(r);
    }

    @Override
    public String toString(){
        return "and";
    }
}

/**
 * represents the or boolean operation
 */
class Or implements BoolNode {
    BoolNode first;
    BoolNode second;

    public Or(BoolNode first, BoolNode second){
        this.first = first;
        this.second = second;
    }

    @Override
    public boolean evaluate(Robot r) {
        return first.evaluate(r) || second.evaluate(r);
    }

    @Override
    public String toString(){
        return "or";
    }
}

/**
 * represents the not boolean operation
 */
class Not implements BoolNode {
    BoolNode first;

    public Not(BoolNode first){
        this.first = first;
    }

    @Override
    public boolean evaluate(Robot r) {
        return !first.evaluate(r);
    }

    @Override
    public String toString(){
        return "not";
    }
}

/**
 * Class that sets a variable to a value at runtime
 */
class SetVariable implements ProgramNode {
    private String key;
    private IntNode value;

    public SetVariable(String key, IntNode value){
        this.key = key;
        this.value = value;
    }

    @Override
    public void execute(Robot robot) {
        Variable.add(key, value.evaluate(robot));
    }

    @Override
    public String toString(){
        return(String.format("Set Variable: %s", key));
    }
}

/**
 * used a variable
 */
class useVariable implements IntNode{
    private String key;

    public useVariable(String key){
        this.key = key;
    }

    @Override
    public int evaluate(Robot r) {
        return Variable.get(key);
    }
    
    @Override
    public String toString(){
        return(String.format("Use Variable: %s", key));
    }
}

/**
 * Stores static vairables that can be accessed anywhere within the abstract syntax tree
 */
class Variable{
    private static Map<String, Integer> variable = new HashMap<>();

    public static void add(String key, int value){
        variable.put(key, value);
    }

    public static int get(String key){
        if (variable.keySet().contains(key)){ return variable.get(key); }
        else { add(key, 0); return 0;}
    }

    @Override
    public String toString(){
        return(String.format("All Variable: %s", variable.toString()));
    }
}