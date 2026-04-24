# API SPEC

## General Introduction

It uses a standard JSON based API to communicate with the mother process throught standard I/O. We will discuss each parameter it can accept and some examples in the end.

## Body

### type

This represents the category the UI wants to act upon. It can be:

- "server" to manage initial installation.
- "modding" to install mods.
- "hardware" to manage server instance configuration.(RAM allocation, server properties...)
- "kill" to end the background service gracefully.

## subType

This represents the type of request in each category. It is uniquely defined for each.

(_For now only Modrinth is fully defined_)

### modrinth

- search - to search based parameters - "modname","version" and "loader".
  _modName_ is the actual search term. _version_ is the game version it needs to be compaitible with. _loader_ is the mod loader the user is using.
- home - Gives a generic top mods list to the UI to display if the user hasn't typed in a query yet.
- download - download gives the exact mod that has the slug "modName" and the recommended meets the "version" and "loader" requirments.

### curseAPI

- search - to search based on parameters identical to modrinth.
- home - same as modrinth, forgot to mention, you should fill the search parameters with dummy data for this. Don't worry the backend will ignore it.
- download - same as modrinth but also needs the parameter "modId".

### Note:

Other stuff in other categories work but those are too incomplete to need a spec.
Here are some generic queries you can try.

- {"type": "modding", "modBrowser": "modrinth", "subType": "search", "modName": "sodium", "version": "1.20.4", "modLoader": "fabric"}
- {"type": "modding", "modBrowser": "modrinth", "subType": "home", "modName": "mods", "version": "1.20.4", "modLoader": "fabric"}
- {"type": "modding", "modBrowser": "modrinth", "subType": "download", "modName": "sodium", "version": "1.20.4", "modLoader": "fabric"}
- {"type": "server", "gameVersion": "1.20.4", "serverVersion": "0.15.7", "serverType": "fabric", "subType": "homepage"}
