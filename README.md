# Simple Weapon Mod Generator

A utility for building Warhammer 40k: Rogue Trader mods for performing simple weapon data patches. The patches generated can be combined with standard OMM mods or can be used standalone to create simple mods that change numbers and basic weapon behaviors.

## Requirements

The mod generator utility requires Java to function and a spreadsheet editor of the user's choice. To check if you have Java installed open a terminal (Command Prompt or Powershell on Windows or Terminal on Linux or Mac) and enter the following:
```
java --version
```
If this command produces an output you have Java installed and are good to go. If you do not you can download Java at https://www.oracle.com/java/technologies/downloads/ or https://jdk.java.net/24/ (choose the latest JDK and installer for your operating system) and install it. After you install it you can reopen your terminal and rerun the command to confirm that it worked.

For a spreadsheet editor it's easiest to use an editor that runs on your machine and can directly edit files on your disk,  If you do not have a spreadsheet editor Google Sheets (https://sheets.google.com) can be used by importing and exporting files from your computer.

## Usage

The mod generator uses a Java jar utility as well to generate tsv data that can be edited in any spreadsheet editor which will then be transformed into the patch format used by Rogue Trader. For simplest usage download the latest utility zip from https://github.com/Mawootad/SimpleWeaponModGenerator/releases and unzip it into a folder of your choice, then follow the following steps.

### 1) (Optional) Baseline Generation

The full utility zip contains baseline data, however it may be out of date. If it seems to be out of date you may need to generate a new baseline. To do so, extract WhRtModificationTemplate.tar from the Modding folder of your Rogue Trader install folder