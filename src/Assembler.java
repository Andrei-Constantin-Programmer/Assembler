import com.sun.jdi.ArrayReference;
import com.sun.source.tree.ThrowTree;
import com.sun.source.tree.Tree;

import java.awt.*;
import java.io.BufferedReader;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.*;

/**
 * An assembler that takes as input a file name that contains Shack language code and turns it into Hack code (which is outputted to a different file).
 *
 * @author Andrei Constantin (ac2042)
 */
public class Assembler
{
    private static HashMap<String, Convertor> conversionMapping;

    //private static ArrayList<String> instructionCodes;
    private static HashMap<String, String> noOperandInstructionMapping;
    private static HashMap<String, String> dRegisterInstructionMapping;
    private FileWriter writer;
    private boolean decArea;

    private TreeSet<String> variables; //RAM labels
    private ArrayList<String> labels; //ROM labels

    private Set<String> variablesNotFound; //set of all not found variable labels exceptions
    private Set<LabelNotFoundException> labelsNotFound; //set of all not found instruction labels exceptions

    private ArrayList<String> goodInstructions;

    public static void main(String[] args)
    {
        String file = args[0];
        //Test if a file has been given and if it has the appropriate suffix.
        if(file!=null && !file.isEmpty() && file.endsWith(".shk"))
        {
            try{
                //Create an assembler with the same name as the given file.
                Assembler assembler = new Assembler(file.substring(0, file.indexOf(".")));
                BufferedReader reader = new BufferedReader(new FileReader(file));

                String line;
                while((line=reader.readLine()) != null)
                {
                    //instructions.add(line);
                    try {
                        assembler.assemble(line, true);
                       //assembler.assemble(instructions.get(instructions.size() - 1), true);
                    }catch(Exception ex) {
                        System.err.println(ex.getMessage());
                    }
                }

                for(String instruction: assembler.goodInstructions)
                    try {
                        assembler.assemble(instruction, false);
                    }catch(Exception ex){
                        System.err.println(ex.getMessage());
                    }

                reader.close();

                //Print all label errors (if any)
                assembler.printLabelErrors();

                assembler.closeWriter();
            }catch(IOException ex)
            {
                System.err.println("Unable to read "+file);
            }
        }
        else
            System.err.println("Usage: sham file.shk");
    }

    /**
     * Constructor of the assembler. It generates the output file.
     *
     * @param fileName The file name that will be used to create the output file.
     */
    public Assembler(String fileName)
    {
        goodInstructions = new ArrayList<>();

        try{
            writer = new FileWriter(fileName+".asm");
        }catch(IOException ex)
        {
            System.err.println("There was an error creating the output file.");
        }
        variables = new TreeSet<>();
        labels = new ArrayList<>();

        variablesNotFound = new LinkedHashSet<>(); //saves the exceptions in order
        labelsNotFound = new TreeSet<>(); //saves the exceptions

        noOperandInstructionMapping = new HashMap<>(){{
            put("INC", "D=D+1");
            put("DEC", "D=D-1");
            put("CLR", "D=0");
            put("NEG", "D=-D");
            put("NOT", "D=!D");
        }};

        dRegisterInstructionMapping = new HashMap<>(){{
            put("ADDD", "+");
            put("ANDD", "&");
            put("ORD", "|");
            put("SUBD", "-");
        }};

        //Create the conversion mapping
        conversionMapping = new HashMap<>()
        {{
            put("ADDD", (line)->writeDRegister(line));
            put("ANDD", (line)->writeDRegister(line));
            put("ORD", (line)->writeDRegister(line));
            put("SUBD", (line)->writeDRegister(line));

            put("INC", (line)->writeNoOperand(line));
            put("DEC", (line)->writeNoOperand(line));
            put("CLR", (line)->writeNoOperand(line));
            put("NEG", (line)->writeNoOperand(line));
            put("NOT", (line)->writeNoOperand(line));

            put("STO", (line)->writeStore(line));
            put("LOAD", (line)->writeLoad(line));

            put("JMP", (line)->writeJump(line));
            put("JGT", (line)->writeJump(line));
            put("JEQ", (line)->writeJump(line));
            put("JGE", (line)->writeJump(line));
            put("JLT", (line)->writeJump(line));
            put("JNE", (line)->writeJump(line));
            put("JLE", (line)->writeJump(line));
        }};
    }

    /**
     * Check if the label is not an instruction
     * @param label The label
     */
    private void checkLabelIsNotInstruction(String label)
    {
        if(conversionMapping.containsKey(label))
            throw new InstructionAsLabelException(label);
    }

    /**
     * Print the ROM label errors
     */
    public void printLabelErrors()
    {
        for(LabelNotFoundException ex: new TreeSet<>(labelsNotFound))
            System.err.println(ex.getMessage());
    }

    /**
     * Closes the file writer
     */
    public void closeWriter()
    {
        try{
            writer.close();
        }
        catch(IOException ex)
        {
            System.err.println("The output file could not be closed.");
        }
    }

    /**
     * Write the given string to the output file.
     * @param toWrite The string to write.
     */
    private void write(String toWrite)
    {
        try{
            writer.write(toWrite+System.getProperty("line.separator"));
        }catch(IOException ex){
            System.err.println("There was an error writing the string.");
        }
    }

    /**
     * Sanitise the instruction by removing unnecessary white spaces.
     * @param instruction The instruction
     * @return The sanitised instruction
     */
    private String sanitiseInstruction(String instruction)
    {
        return instruction.strip().replaceAll("[\\n\\t]", "").replaceAll("\\s+", " ");
    }

    /**
     * Takes the given Shack line and converts it into Hack instructions
     * @param instruction The line of Shack code to be converted
     * @param isDeclaring Whether this is the declaration faze (no conversions)
     */
    public void assemble(String instruction, boolean isDeclaring)
    {
        //Sanitise the input
        instruction = sanitiseInstruction(instruction);

        //Check if the line is a comment. If it is, no more converting needs to happen.
        if(instruction.isEmpty())
            return;
        if(instruction.startsWith("//")) {
            /*if(isDeclaring)
                goodInstructions.add(instruction);
            else
                write(instruction);*/
            return;
        }
        //Check if the line is equal to .dec or .code and set the boolean variable 'declaring' accordingly.
        if(instruction.equals(".dec"))
        {
            if(isDeclaring)
                goodInstructions.add(instruction);
            decArea = true;
            return;
        }
        if(instruction.equals(".code"))
        {
            if(isDeclaring)
                goodInstructions.add(instruction);
            decArea = false;
            return;
        }

        //Do declarations
        if(decArea)
        {
            if(isDeclaring) {
                checkInvalidChar(instruction, false);
                checkLabelIsNotInstruction(instruction);
                if(!variables.contains(instruction))
                    goodInstructions.add(instruction);
                variables.add(instruction);
            }
            /*else
            {
                write("@" + instruction);
            }*/
        }
        else //Do instructions
        {
            //Do instruction label
            if(instruction.charAt(instruction.length()-1)==':')
            {
                //Remove the : at the end of the label
                instruction=instruction.substring(0, instruction.length()-1);

                //If I am in the declaring phase, I put the label inside the instruction labels list
                if(isDeclaring) {
                    //Check if the label is valid and if it already exists
                    checkInvalidChar(instruction, false);
                    checkLabelIsNotInstruction(instruction);
                    if(labels.contains(instruction))
                        throw new LabelAlreadyExistsException(false, instruction);
                    if(variables.contains(instruction))
                        throw new LabelAlreadyExistsException(true, instruction);

                    goodInstructions.add(instruction+":");
                    labels.add(instruction);
                }else //Otherwise, convert it
                {
                    write("(" + instruction + ")");
                }
            }
            else //Do actual instruction
            {
                //If I am in the declaring phase, I don't yet convert the other instructions
                if(!isDeclaring)
                    convertInstruction(instruction);
                else
                    goodInstructions.add(instruction);
            }
        }
    }

    /**
     * Find invalid character in the given code.
     * @param code The code
     * @param canStartWithNumber true if the destination can start with a number, false otherwise
     *
     * @return the invalid char, or a space character if none were found
     */
    private char findInvalidChar(String code, boolean canStartWithNumber)
    {
        String pattern = "^[\\w]+$";
        String alphabet = "^[a-zA-Z]+$";
        //Check if the first character is a letter (if the code cannot start with a number)
        if(!canStartWithNumber && !(code.charAt(0)+"").matches(alphabet))
        {
            return code.charAt(0);
        }
        //Check if the code is alphanumeric (including underscores)
        for(int i=0; i<code.length(); i++)
            if(!(code.charAt(i)+"").matches(pattern))
            {
                return code.charAt(i);
            }


        return ' ';
    }

    /**
     * Check if an invalid character exists in the given code. If so, an IllegalCharacterException is thrown.
     * @param code The code
     * @param canStartWithNumber true if the destination can start with a number, false otherwise
     */
    private void checkInvalidChar(String code, boolean canStartWithNumber)
    {
        char c = findInvalidChar(code, canStartWithNumber);
        if(c!=' ')
            throw new IllegalCharacterException(c);
    }

    /**
     * Convert the given line to an instruction (if possible). If it is not possible, print out an error.
     * @param line The instruction line
     */
    private void convertInstruction(String line)
    {
        String[] parts = line.split(" ");
        //Search the instruction for invalid characters
        char c = findInvalidChar(parts[0], false);
        if(c!=' ')
            throw new IllegalCharacterException(c);

        if(!conversionMapping.containsKey(parts[0]))
            throw new IllegalInstructionException(line);
        conversionMapping.get(parts[0]).write(line);
    }

    /**
     * Convert load instructions.
     * @param line The instruction
     *
     */
    private void writeLoad(String line)
    {
        String[] parts = line.split(" ");
        //Check instruction validity
        if(parts.length!=3)
            throw new IncorrectNumberOperandsException(parts[0]);
        if(!(parts[1].equals("A") || parts[1].equals("D")))
            throw new IllegalOperandException(parts[1]);

        //Do different things depending on whether the second part is A or D
        if(parts[1].equals("A"))
        {
            if(parts[2].equals("D"))
                write("A=D");
            else
            {
                //Check the validity of the label (if one is provided)
                String dest = parts[2];
                boolean isAddress=false;
                if(!dest.startsWith("#")) isAddress=true;
                dest = getDestination(dest, false);

                if(!dest.equals("-1")) {
                    write("@" + dest);
                    if(isAddress)
                        write("A=M");
                }
            }
        }
        else
        {
            if(parts[2].equals("A"))
                write("D=A");
            else
            {
                //Check the validity of the label (if one is provided)
                String dest = parts[2];
                dest = getDestination(dest, false);
                if(!dest.equals("-1")) {
                    //Save A
                    write("D=A");
                    write("@R13");
                    write("M=D");

                    //Load D
                    write("@" + dest);
                    if (parts[2].startsWith("#"))
                        write("D=A");
                    else
                        write("D=M");
                    //Restore A
                    write("@R13");
                    write("A=M");
                }
            }
        }
    }

    /**
     * Convert store instructions.
     * @param line The instruction
     */
    private void writeStore(String line)
    {
        String[] parts = line.split(" ");
        //Check instruction validity
        if(parts.length!=3)
            throw new IncorrectNumberOperandsException(parts[0]);
        if(!(parts[1].equals("A") || parts[1].equals("D")))
            throw new IllegalOperandException(parts[1]);

        //Check the validity of the label (if one is provided)
        String dest=parts[2];
        if(dest.startsWith("#"))
            throw new IllegalOperandException(dest);
        dest = getDestination(dest, false);
        if(!dest.equals("-1"))
        {
            //Convert the instruction
            if (parts[1].equals("A"))
                write("D=A");
            write("@" + dest);
            write("M=D");
        }
    }

    /**
     * Convert d-register instructions (ADDD, ANDD etc.)
     * @param line The instruction
     */
    private void writeDRegister(String line)
    {
        String[] parts = line.split(" ");
        //Check instruction validity
        if(parts.length!=2)
            throw new IncorrectNumberOperandsException(parts[0]);

        //Check the validity of the label (if one is provided)
        String dest = parts[1];
        dest = getDestination(dest, false);

        if(!dest.equals("-1")) {
            //Convert the instruction
            write("@" + dest);

            if (parts[1].startsWith("#"))
                write("D=D" + dRegisterInstructionMapping.get(parts[0]) + "A");
            else
                write("D=D" + dRegisterInstructionMapping.get(parts[0]) + "M");
        }
    }

    /**
     * Converts a no-operand instruction.
     * @param line The instruction
     */
    private void writeNoOperand(String line)
    {
        String[] parts = line.split(" ");
        //Check instruction validity
        if(parts.length!=1)
            throw new IncorrectNumberOperandsException(parts[0]);

        //Convert the instruction
        write(noOperandInstructionMapping.get(parts[0]));
    }

    /**
     * Convert a jump instruction.
     * @param line The instruction
     *
     */
    private void writeJump(String line)
    {
        String[] parts = line.split(" ");
        //Check instruction validity
        if(parts.length!=2)
            throw new IncorrectNumberOperandsException(parts[0]);

        //Check the validity of the label (if one is provided)
        String destination=parts[1];
        try {
            if(destination.startsWith("#"))
                throw new IllegalOperandException(destination);
            destination = getDestination(parts[1], true);
        }catch(LabelNotFoundException ex) {
            labelsNotFound.add(ex);
            return;
        }

        //Convert the instruction
        write("@"+destination);
        if(parts[0].equals("JMP"))
            write("0; JMP");
        else
            write("D; "+parts[0]);
    }

    /**
     * Get the destination from the given String. If it's a number, return it as it is, otherwise check if the label exists. If
     * the destination is not valid (and no exception is thrown), the destination will be -1
     * @param dest The destination
     * @param isJump true if the destination is a jump destination, false otherwise
     */
    private String getDestination(String dest, boolean isJump)
    {
        if(dest.startsWith("#"))
            dest=dest.substring(1);
        try{
            //Check if it is a label or a number
            long destination = Long.parseLong(dest);

            //Check if the destination is a valid number
            if(destination<0 || destination>32767) {
                throw new IllegalOperandException(dest);
            }
        }catch(NumberFormatException ex)
        {
            checkInvalidChar(dest, true);
            if(isJump)
            {
                if(!labels.contains(dest))
                    if(variables.contains(dest))
                        throw new InvalidJumpTargetException(dest); //If a variable was used as a jump destination, throw an InvalidJumpTargetException
                    else
                    {
                        checkInvalidChar(dest, false);
                        throw new LabelNotFoundException(true, dest); //If the label has not been found, throw a LabelNotFoundException
                    }
            }
            else if(!variables.contains(dest)) {
                checkInvalidChar(dest, false);
                if(!variablesNotFound.contains(dest)) {
                    variablesNotFound.add(dest);
                    throw new LabelNotFoundException(false, dest);
                }
                else
                    dest="-1";
            }
        }

        return dest;
    }

    //region EXCEPTIONS

    /**
     * Exception thrown when a character is invalid.
     */
    private static class IllegalCharacterException extends RuntimeException
    {
        /**
         * Constructor for the IllegalCharacterException based on the supplied character.
         * @param character The character
         */
        private IllegalCharacterException(char character)
        {
            super("Illegal character: "+character);
        }
    }

    private static class IncorrectNumberOperandsException extends RuntimeException
    {
        /**
         * Constructor for the IncorrectNumberOperandsException based on the supplied instruction.
         * @param instruction The instruction
         */
        private IncorrectNumberOperandsException(String instruction)
        {
            super("Incorrect number of operands for "+instruction);
        }
    }

    /**
     * Exception thrown when the instruction is invalid.
     */
    private static class IllegalInstructionException extends RuntimeException
    {
        /**
         * Constructor for the IllegalInstructionException based on the supplied instruction.
         * @param instruction The instruction
         */
        private IllegalInstructionException(String instruction)
        {
            super("Illegal opcode: "+instruction);
        }
    }

    /**
     * Exception thrown when the instruction is invalid.
     */
    private static class IllegalOperandException extends RuntimeException
    {
        /**
         * Constructor for the IllegalOperandException based on the supplied operand.
         * @param operand The operand
         */
        private IllegalOperandException(String operand)
        {
            super("Illegal operand: "+operand);
        }
    }

    /**
     * Exception thrown when a ROM label already exists.
     */
    private static class LabelAlreadyExistsException extends RuntimeException
    {
        /**
         * Constructor for the LabelAlreadyExistsException
         * @param isRAM Whether the label has already been defined as a RAM label or not
         * @param label The label
         */
        private LabelAlreadyExistsException(boolean isRAM, String label)
        {
            super(isRAM?"ROM label "+label+" has been defined as a RAM label.":"ROM label "+label+" has been defined more than once.");
        }
    }

    /**
     * Exception thrown when an instruction code is used as a label
     */
    private static class InstructionAsLabelException extends RuntimeException
    {
        /**
         * Constructor for the InstructionAsLabelException
         * @param label The label
         */
        private InstructionAsLabelException(String label)
        {
            super(label+" is an opcode and may not be used as a label.");
        }
    }

    /**
     * Exception thrown when a label has not been found.
     */
    private static class LabelNotFoundException extends RuntimeException implements Comparable<LabelNotFoundException>
    {
        private final String label;
        /**
         * Constructor for the LabelNotFoundException
         *
         * @param isInstruction Whether this is an instruction label (true) or a variable label (false)
         * @param label The label
         */
        private LabelNotFoundException(boolean isInstruction, String label)
        {
            super(isInstruction?"Instruction label "+label+" has not been defined.":"RAM label "+label+" has not been declared.");
            this.label = label;
        }

        /**
         * Compare two LabelNotFoundExceptions
         * @param ex The exception to compare to
         * @return The result of the comparison
         */
        @Override
        public int compareTo(LabelNotFoundException ex) {
            return this.getMessage().toLowerCase().compareTo(ex.getMessage().toLowerCase());
        }

        /**
         * Get the label
         * @return The label
         */
        public String getLabel()
        {
            return label;
        }
    }

    /**
     * Exception thrown when a RAM label is used for a jump instruction.
     */
    private static class InvalidJumpTargetException extends RuntimeException
    {
        /**
         * Constructor for the InvalidJumpTargetException
         * @param label The label.
         */
        private InvalidJumpTargetException(String label)
        {
            super("RAM label "+label+" has been used as a jump destination.");
        }
    }

    //endregion

    private interface Convertor
    {
        public void write(String line);
    }
}
