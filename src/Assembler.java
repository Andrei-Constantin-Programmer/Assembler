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
    private static HashMap<String, String> noOperandInstructionMapping;
    private static HashMap<String, String> dRegisterInstructionMapping;
    private FileWriter writer;
    private boolean decArea;

    private ArrayList<String> variables; //RAM labels
    private ArrayList<String> labels; //ROM labels

    private Set<LabelNotFoundException> variablesNotFound; //set of all not found variable labels exceptions
    private Set<LabelNotFoundException> labelsNotFound; //set of all not found instruction labels exceptions

    public static void main(String[] args)
    {
        //String file = args[0];
        String file = "example1.shk";
        ArrayList<String> instructions = new ArrayList<>();
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
                    instructions.add(line);
                    assembler.assemble(instructions.get(instructions.size()-1), true);
                }

                for(String instruction: instructions)
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
                System.err.println("There was an error reading from the file. Make sure you have supplied the necessary file.");
            }
        }
        else
            System.err.println("Please give as parameter the name of a file with the suffix '.shk'");
    }


    /**
     * Constructor of the assembler. It generates the output file.
     *
     * @param fileName The file name that will be used to create the output file.
     */
    public Assembler(String fileName)
    {
        try{
            writer = new FileWriter(fileName+".asm");
        }catch(IOException ex)
        {
            System.err.println("There was an error creating the output file.");
        }
        variables = new ArrayList<>();
        labels = new ArrayList<>();

        variablesNotFound = new LinkedHashSet<>(); //saves the exceptions in order
        labelsNotFound = new TreeSet<>(); //saves the exceptions alphanumerically

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
    }

    /**
     * Print the label errors (RAM labels not declared, ROM labels not defined)
     */
    public void printLabelErrors()
    {
        //Print RAM label errors
        for(LabelNotFoundException ex: variablesNotFound)
        {
            System.err.println(ex.getMessage());
        }

        //Print ROM label errors
        for(LabelNotFoundException ex: labelsNotFound)
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
     */
    public void assemble(String instruction, boolean isDeclaring)
    {
        //Sanitise the input
        instruction = sanitiseInstruction(instruction);

        //Check if the line is a comment. If it is, no more converting needs to happen.
        if(instruction.startsWith("//") || instruction.isEmpty())
            return;

        //Check if the line is equal to .dec or .code and set the boolean variable 'declaring' accordingly.
        if(instruction.equals(".dec"))
        {
            decArea =true;
            return;
        }
        if(instruction.equals(".code"))
        {
            decArea =false;
            return;
        }

        //Do declarations
        if(decArea)
        {
            //If I am in the declaring phase, I put the label inside the variable labels list
            if(isDeclaring) {
                //Check if the label is valid
                if(!isValidLabel(instruction))
                    throw new InvalidLabel(false, instruction);

                variables.add(instruction);
            }
            else //Otherwise, I convert it (its validity has already been checked previously)
                write("@"+instruction);
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
                    if(!isValidLabel(instruction) || labels.contains(instruction))
                        throw new InvalidLabel(true, instruction);
                    labels.add(instruction);
                }else //Otherwise, I convert it
                    write("@"+instruction);
            }
            else //Do actual instruction
            {
                //If I am in the declaring phase, I don't yet convert the other instructions
                if(!isDeclaring)
                    convertInstruction(instruction);
            }
        }
    }

    /**
     * Check if the label is valid.
     * @param label The label
     *
     * @return true if the label is valid, false if it is invalid
     */
    private boolean isValidLabel(String label)
    {
        String pattern = "^[\\w]+$";
        String alphabet = "^[a-zA-Z]+$";
        //Check if the first character is a letter
        if(!label.substring(0, 1).matches(alphabet))
            return false;
        if(variables.contains(label))
            return false;

        //Check if the label is alphanumeric (including underscores)
        return label.matches(pattern);
    }

    /**
     * Convert the given line to an instruction (if possible). If it is not possible, print out an error.
     * @param line The instruction line
     */
    private void convertInstruction(String line) throws IllegalInstructionException, LabelNotFoundException
    {
        String[] parts = line.split(" ");
        //Select the instruction to be done
        switch (parts[0]) {
            //D-register instructions
            case "ADDD", "ANDD", "ORD", "SUBD" -> writeDRegister(line);

            //No operand instructions
            case "INC", "DEC", "CLR", "NEG", "NOT" -> writeNoOperand(line);

            //Jump instructions
            case "JMP", "JGT", "JEQ", "JGE", "JLT", "JNE", "JLE" -> writeJump(line);

            //Store instruction
            case "STO" -> writeStore(line);

            //Load instruction
            case "LOAD" -> writeLoad(line);
            default -> System.err.println("The instruction " + parts[0] + " does not exist.");
        }
    }

    /**
     * Convert load instructions.
     * @param line The instruction
     *
     * @throws IllegalInstructionException throws illegal instruction exception
     * @throws LabelNotFoundException throws label not found exception
     */
    private void writeLoad(String line) throws IllegalInstructionException, LabelNotFoundException
    {
        String[] parts = line.split(" ");
        //Check instruction validity
        if(parts.length!=3)
            throw new IllegalInstructionException(line);
        if(!(parts[1].equals("A") || parts[1].equals("D")))
            throw new IllegalInstructionException(line);

        //Do different things depending on whether the second part is A or D
        if(parts[1].equals("A"))
        {
            if(parts[2].equals("D"))
                write("A=D");
            else
            {
                //Check the validity of the label (if one is provided)
                String dest = parts[2];
                if(dest.startsWith("#"))
                    dest=dest.substring(1);
                try{
                    dest = getDestination(dest, false);
                }catch(LabelNotFoundException ex){
                    variablesNotFound.add(ex);
                    return;
                }
                write("@"+dest);
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
                if(dest.startsWith("#"))
                    dest=dest.substring(1);
                try{
                    dest = getDestination(dest, false);
                }catch(LabelNotFoundException ex){
                    variablesNotFound.add(ex);
                    return;
                }

                //Save A
                write("D=A");
                write("@R13");
                write("M=D");

                //Load D
                write("@"+dest);
                if(parts[2].startsWith("#"))
                    write("D=A");
                else
                    write("D=M");
                //Restore A
                write("@R13");
                write("A=M");
            }
        }
    }

    /**
     * Convert store instructions.
     * @param line The instruction
     *
     * @throws IllegalInstructionException throws illegal instruction exception
     * @throws LabelNotFoundException throws label not found exception
     */
    private void writeStore(String line) throws IllegalInstructionException, LabelNotFoundException
    {
        String[] parts = line.split(" ");
        //Check instruction validity
        if(parts.length!=3)
            throw new IllegalInstructionException(line);
        if(!(parts[1].equals("A") || parts[1].equals("D")))
            throw new IllegalInstructionException(line);

        //Check the validity of the label (if one is provided)
        String dest=parts[2];
        try{
            dest = getDestination(dest, false);
        }catch(LabelNotFoundException ex){
            variablesNotFound.add(ex);
            return;
        }

        //Convert the instruction
        if(parts[1].equals("A"))
            write("D=A");
        write("@"+dest);
        write("M=D");
    }

    /**
     * Convert d-register instructions (ADDD, ANDD etc.)
     * @param line The instruction
     *
     * @throws IllegalInstructionException throws illegal instruction exception
     * @throws LabelNotFoundException throws label not found exception
     */
    private void writeDRegister(String line) throws IllegalInstructionException, LabelNotFoundException
    {
        String[] parts = line.split(" ");
        //Check instruction validity
        if(parts.length!=2)
            throw new IllegalInstructionException(line);
        //Check if the instruction has a sign associated with it
        if(dRegisterInstructionMapping.get(parts[0])==null)
            throw new IllegalInstructionException(line);

        //Check the validity of the label (if one is provided)
        String dest = parts[1];
        if(dest.startsWith("#"))
            dest=dest.substring(1);
        try{
            dest = getDestination(dest, false);
        }catch(LabelNotFoundException ex){
            variablesNotFound.add(ex);
            return;
        }

        write("@"+dest);

        //Convert the instruction
        if(parts[1].startsWith("#"))
            write("D=D"+dRegisterInstructionMapping.get(parts[0])+"A");
        else
            write("D=D"+dRegisterInstructionMapping.get(parts[0])+"M");
    }

    /**
     * Converts a no-operand instruction.
     * @param line The instruction
     *
     * @throws IllegalInstructionException throws illegal instruction exception
     */
    private void writeNoOperand(String line) throws IllegalInstructionException
    {
        String[] parts = line.split(" ");
        //Check instruction validity
        if(parts.length!=1)
            throw new IllegalInstructionException(line);

        //Check if the instruction has a conversion String associated with it
        if(noOperandInstructionMapping.get(parts[0])==null)
            throw new IllegalInstructionException(line);

        //Convert the instruction
        write(noOperandInstructionMapping.get(parts[0]));
    }

    /**
     * Convert a jump instruction.
     * @param line The instruction
     *
     * @throws IllegalInstructionException throws illegal instruction exception
     * @throws LabelNotFoundException throws label not found exception
     */
    private void writeJump(String line) throws IllegalInstructionException, LabelNotFoundException
    {
        String[] parts = line.split(" ");
        //Check instruction validity
        if(parts.length!=2)
            throw new IllegalInstructionException(line);

        //Check the validity of the label (if one is provided)
        String destination;
        try {
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
     * Get the destination from the given String. If it's a number, return it as it is, otherwise check if the label exists.
     * @param dest The destination
     * @param isJump true if the destination is a jump destination, false otherwise
     *
     * @throws LabelNotFoundException throws label not found exception
     * @throws InvalidJumpTargetException throws invalid jump target exception
     */
    private String getDestination(String dest, boolean isJump) throws LabelNotFoundException, InvalidJumpTargetException
    {
        try{
            //Check if it is a label or a number
            int destination = Integer.parseInt(dest);
            //Check if the destination is a valid number
            if(destination<0 || destination>32767)
                throw new InvalidJumpTargetException(dest, false);
        }catch(Exception ex)
        {
            if(isJump)
            {
                if(!labels.contains(dest))
                    if(variables.contains(dest))
                        throw new InvalidJumpTargetException(dest, true); //If a variable was used as a jump destination, throw an InvalidJumpTargetException
                    else
                        throw new LabelNotFoundException(true, dest); //If the label has not been found, throw a LabelNotFoundException
            }
            else if(!variables.contains(dest))
                throw new LabelNotFoundException(false, dest);
        }

        return dest;
    }

    //region EXCEPTIONS

    /**
     * Exception thrown when a character is invalid.
     */
    private static class InvalidCharacter extends RuntimeException
    {
        /**
         * Constructor for the InvalidCharacter based on the supplied character.
         * @param character The character
         */
        private InvalidCharacter(char character)
        {
            super("The character "+character+" is invalid.");
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
            super("The instruction "+instruction+" is illegal.");
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
            super("The operand "+operand+" is illegal.");
        }
    }

    /**
     * Exception thrown when a label has not been found.
     */
    private static class LabelNotFoundException  extends RuntimeException implements Comparable<LabelNotFoundException>
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
            return this.getMessage().compareTo(ex.getMessage());
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
        private InvalidJumpTargetException(String label, boolean isRAMLabel)
        {
            super(isRAMLabel?"RAM label "+label+" has been used as a jump destination.":"The number "+label+" is not a valid jump target.");
        }
    }

    /**
     * Exception thrown when a label is invalid.
     */
    private static class InvalidLabel extends RuntimeException
    {
        /**
         * Constructor for the LabelNotFoundException
         * @param label The label that was not found.
         */
        private InvalidLabel(boolean isInstruction, String label)
        {
            super(isInstruction?"Instruction label "+label+" is invalid.":"RAM label "+label+" is invalid.");
        }
    }

    //endregion
}
