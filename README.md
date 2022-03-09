# Assembler
## What is an assembler?
*"An assembler program creates object code by translating combinations of mnemonics and syntax for operations and addressing modes into their numerical equivalents."* ([Wikipedia](https://en.wikipedia.org/wiki/Assembly_language#Assembler))
  
The Assembler that I have created serves the purpose of converting Shack language code to Hack language code.

## What is Shack?
Shack is a language invented by my University professor, [David Barnes](https://www.kent.ac.uk/computing/people/3070/barnes-david), with the express purpose to test our understanding of creating 
an Assembler. I took an Object-Oriented approach, using Java, to solve this task. The course itself is based on [Nand2Tetris](https://www.nand2tetris.org/), with the purpose of understanding computer systems
from the basic NAND gate all the way to the game Tetris (though the course only uses part of this, with some modifications).

## What is Hack?
Hack is a language created by the people of Nand2Tetris as a substitute for more advanced low-level languages such as Assembly. It can be transformed into binary code and then run on the Computer built earlier in
the course.
  
---
  
# Install and Run

## Prerequisites
- Java installed on the machine
- An IDE that can run Java programs (IntelliJ, Eclipse etc.)

## How to install and run
1. Clone the project in your IDE of choice from this link: https://github.com/Andrei-Constantin-Programmer/Assembler.git
2. Run the 'main' method from the Assembler class (make sure to supply the name of the file as a string in the parameters of the method e.g.: "filename.shk")
3. The assembler will create a corresponding '.hack' file with the assembled code in the same place as the provided '.shk' file. 
4. The resulting Hack code can be run through the CPU emulator from the [Nand2Tetris software suite](https://www.nand2tetris.org/software)
  
---

# Special thanks
1. Special thanks to David Barnes for setting the assignment and creating the Shack language 
2. The Nand2Tetris creators for building the foundation blocks for this project.
