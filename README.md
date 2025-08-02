# Simple Weapon Mod Generator

A utility for building Warhammer 40k: Rogue Trader mods for performing simple weapon data patches. The patches generated
can be combined with standard OMM mods or can be used standalone to create simple mods that change numbers and basic
weapon behaviors.

## Requirements

The mod generator utility requires Java to function and a spreadsheet editor of the user's choice. To check if you have
Java installed open a terminal (Command Prompt or Powershell on Windows or Terminal on Linux or Mac) and enter the
following:

```shell
java --version
```

If this command produces an output you have Java installed and are good to go. If you do not you can download Java
at https://www.oracle.com/java/technologies/downloads/ or https://jdk.java.net/24/ (choose the latest JDK and installer
for your operating system) and install it. After you install it you can reopen your terminal and rerun the command to
confirm that it worked.

For a spreadsheet editor it's easiest to use an editor that runs on your machine and can directly edit files on your
disk, If you do not have a spreadsheet editor Google Sheets (https://sheets.google.com) can be used by importing and
exporting files from your computer.

## Usage

The mod generator uses a Java jar utility as well to generate tsv data that can be edited in any spreadsheet editor
which will then be transformed into the patch format used by Rogue Trader. For simplest usage download the latest
utility zip from https://github.com/Mawootad/SimpleWeaponModGenerator/releases and unzip it into a folder of your
choice, then follow the following steps.

### 1) Create a baseline (optional)

The full utility zip contains baseline data, however it may be out of date. If it seems to be out of date you may need
to generate a new baseline. To do so, extract WhRtModificationTemplate.tar from the Modding folder of your Rogue Trader
install folder and run the following command:

```shell
java -jar SimpleWeaponModGenerator.jar regenerate make_tsv --obtainable --template_path="{PATH_TO_TEMPLATE}"
```

Where `{PATH_TO_TEMPLATE}` is the location of the folder where you extracted the template.

### 2) Make edits

Open the `weapons.tsv` file in your mod folder in the spreadsheet editor of your choice, it will contain data on the
various weapons that are obtainable in Rogue Trader. To make edits you can either directly modify the spreadsheet or you
can create a new spreadsheet and copy the spreadsheet header and any weapons you want to modify there. Changes you make
here will be used by the utility to create a mod based on the changes you make.

### 3) Verify your changes

When you are satisfied with the changes you've made save your updated tsv file to the 'modifications' folder, then run
the following command:

```shell
java -jar SimpleWeaponModGenerator.jar remove_duplicate_info
```

This command will remove all fields except for blueprint name, display name, and description that are identical in the
base game and your modified data. The resulting data will be exactly the modification that will be generated from your
changes. Review this data and if it looks correct then continue to the next step. Otherwise you can make further edits
until you're satisfied.

### 4) Create a manifest

Open the provided manifest.json file in the text editor of your choice and fill out the fields there. You will need a
UniqueName, which should be a name without any spaces or punctuation and unique to your mod, and a Version. The other
fields (Description, Author, Repository, and HomePage) are optional but highly recommended if you have them. The
following shows an example of a completed manifest file:

```json
{
  "UniqueName": "BetterKnives",
  "Version": "1.0.0",
  "DisplayName": "Better Knives",
  "Description": "Makes knives a more reasonable weapon type",
  "Author": "Mawootad",
  "Repository": "https://github.com/Mawootad/SimpleWeaponModGenerator",
  "HomePage": "",
  "Dependencies": []
}
```

Once you've filled out your manifest you're ready to generate your final mod.

### 5) Create a mod zip

Once you are satisfied with your weapon changes and your manifest is complete run the following command to create your
mod zip:

```shell
java -jar SimpleWeaponModGenerator.jar make_patches make_zip
```

If there are no errors with your changes this will generate a zip file for your mod. You can use a tool like ModFinder
to install it and test it. If you want you can also upload it for others to use, or you can just use it to make changes
to weapons for your own personal enjoyment.

## Advanced Usage

### Combining with standard OMM mods

The modification utility allows mixing changes configured via tsv with changes made via the Unity editor, and will
automatically merge patch modification assets, pull in any .jpb, .jpb_patch, and .patch files specified for inclusion in
your mod, and copy any json files included in your Localizations folder into a new mod while building. Note that the
utility does not support C# scripts, added icons, or other resource files; if your patch contains any of these you will
need to make the tsv-based .patch files and modifications config via
`java -jar SimpleWeaponModGenerator.jar make_patches` and then manually merge the patches and modification config by
copying the patches in generated/Blueprints to the mod's main Blueprints folder and by copying the entries in
generatedPatchesConfig.asset to you're mod's main patch config (this can be done very easily via a text editor).

## Features not currently implemented

The following features would be useful but aren't currently implemented. If you need one of these features feel free to
submit an issue or add an implementation and make a pull request.

- Changing weapon stat requirements
- Changing weapon feature requirements
- Removing added weapon features
- Modifying stackable weapon features (eg: bleed amount on axes, power level of psyker staves)
- Adding/changing generic weapon text blurbs (eg: "Most warriors prefer sword for they provide more flexibility on the
  battlefield against close combat enemies.")
- Changing names/descriptions for weapons
- Merging OMM patches and generated patches on the same weapon
