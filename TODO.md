# TODOs

## Done

### Messages in AI Updater
- [x] Command to activate/deactivate notification on server join that AI model is up to date
- [x] Status shown when no argument is provided
- [x] Added a button in AI menu to toggle ON/OFF

### Coop Join Warning
- [x] Add blacklist warning when a player asks to join your coop
- [x] Add security like for Email and Discord with bypass block for `/coopadd`
- [x] Send warning when you invite a blacklisted player (`You invited <name> to your co-op!`)
- [x] Send additional warning when blacklisted player joined (`<name> joined your SkyBlock Co-op!`)
- [x] `/coopadd` is always blocked first and requires `[BYPASS]`

### Location Backbone
- [x] Grab the information of the current location via the scoreboard
- [x] Added `LocationService` and `SkyblockIsland` enum with case-insensitive matching

### ModMenu https://modrinth.com/mod/modmenu
- [x] Implemented as soft dependency
- [x] Added ModMenu config entrypoint to open ScamScreener settings

## Open

### ChatGPT Stage
- [ ] implement ChatGPT API Backbone which only returns a JSON String with a Score for the Scam Evaluation
- [ ] implement LLM Stage for context understanding
- [ ] decide when to ask ChatGPT and when not, to avoid unnecessary API responses and costs
- [ ] implement a protection against abusive behavior towords API requests, to avoid unnecessary costs
- [ ] make sure no private information is going through ChatGPTs API. 
- [ ] use ChatGPTs Evaluation to train Local API, furthermore the goal is to use API less every time a request is send. so local API might be possible to understand simple context

### Karma System
- [ ] implement a Karma System, reward players that are being nice to you
> Check for Hypixel Rules to use such a system as it might be against the rules to keep track of players behavior

### Dungeon Death Tracker
- [ ] Keep track of People often dying in Dungeons. store them in a file with reason and count it up everytime they die
- [ ] Also fetch from Hypixel API the number of runs the players in party did to calculate a Ratio
- [ ] send warning if a Player that dies often is in your dungeon party. optional auto-leave
- [ ] add this festure to the Settings Menu

