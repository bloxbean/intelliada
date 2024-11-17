[![Java CI with Gradle](https://github.com/bloxbean/intelliada/actions/workflows/gradle.yml/badge.svg)](https://github.com/bloxbean/intelliada/actions/workflows/gradle.yml)

![IntelliAda](https://user-images.githubusercontent.com/35016438/121795762-5d4c7180-cc46-11eb-8d67-412fda27bea4.png)

IntelliJ plugin for Cardano blockchain. Using this plugin developers can interact with Cardano network directly from
their IDE. In the current version of this plugin, developers can mint native tokens, transfer Ada / Native tokens from
one address to another inside their IDE. The plugin provides integration with [Blockfrost API](https://blockfrost.io),
so the developers do not need to install cardano-node or cardano-cli on their local machine to interact with Cardano
network.

[Documentation](https://intelliada.bloxbean.com/)

### Features

* Account Management
    * Create new testnet/mainnet accounts
    * Import an account by providing 24w mnemonic phrase
* Transfer transaction
    * Transfer Ada or any native tokens
* Native Token Minting
* Script Management
    * Create script \(sig, all, any, atLeast, after, before\)
    * Use policy scripts while minting new native tokens
* Metadata support
    * Add metdata to the transaction
    * Supports raw json
    * Metadata editor to build metadata
    * Metadata templates
* View transactions
* Utxo explorer
    * View utxos of a specific address
    * Select utxos during transaction submission \(Optional\)
* Blockfrost integration
    * Integrates with Blockfrost Cardano Api

### Supported IntelliJ version \(2024.2 and above\)

* IntelliJ IDEA
* PyCharm
* WebStorm

### **Installation**

1. Go to Preferences &gt; Plugins or File &gt; Settings &gt; Plugins
2. Search "IntelliAda" in Marketplace tab
3. Click "Install"

### Build from Source

```
git clone https://github.com/bloxbean/intelliada.git
./gradlew clean build
```
