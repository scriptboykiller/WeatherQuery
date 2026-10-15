好的，这里是在 VS Code 中配置 minininja/db-mcp-server 连接你远程 H2 数据库的完整步骤。

📋 第一步：环境准备

· Java：确保安装了 JDK 17 或更高版本（推荐 21）。
· Maven：用于构建项目。
· Git：用于克隆仓库。

📦 第二步：构建 JAR 包

1. 打开终端，克隆项目：
   ```bash
   git clone https://github.com/minininja/db-mcp-server.git
   cd db-mcp-server
   ```
2. 构建 fat JAR（包含 H2 驱动）：
   ```bash
   mvn clean install
   ```
   构建成功后，核心 JAR 位于 db-mcp-core/target/db-mcp-core-1.0.0.jar。

⚙️ 第三步：在 VS Code 中配置 MCP 服务器

1. 在项目根目录创建 .vscode/mcp.json 文件（若无 .vscode 文件夹则手动创建）。
2. 填入以下配置，请替换为你实际的数据库名、用户名和密码：
   ```json
   {
     "servers": {
       "h2-db": {
         "type": "stdio",
         "command": "java",
         "args": [
           "-jar",
           "/path/to/db-mcp-core-1.0.0.jar"
         ],
         "env": {
           "DB_TYPE": "h2",
           "DB_HOST": "uklvadapp196.uk.standardchartered.com",
           "DB_PORT": "19092",
           "DB_NAME": "你的数据库名",
           "DB_USER": "你的用户名",
           "DB_PASSWORD": "你的密码"
         }
       }
     }
   }
   ```
   配置说明：
   · type: 固定为 "stdio"，表示通过标准输入输出通信。
   · command: 启动命令，使用 java。
   · args: JAR 包的绝对路径，务必替换为你的实际路径。
   · env: 环境变量，H2 默认端口为 9092，你的为 19092，DB_NAME 请填写实际数据库名（默认 test）。

🚀 第四步：启动并验证

1. 在 VS Code 中，打开 GitHub Copilot Chat，切换到 Agent 模式。
2. 点击 MCP 服务器图标，找到 h2-db，点击启动按钮。
3. 若配置正确，服务器状态会显示为 Running。
4. 在对话中输入测试指令，如 “使用 #h2-db 列出所有表”。若能返回表名列表，说明连接成功。

🔧 第五步：故障排查

· 连接被拒绝：检查 H2 服务器是否运行在 19092 端口，以及网络是否可达。
· 认证失败：核对 DB_USER 和 DB_PASSWORD 是否正确。
· 驱动问题：确认 JAR 包路径正确，且包含了 H2 JDBC 驱动（fat JAR 已内置）。
· 数据库名错误：确认 DB_NAME 与实际数据库名一致。

连接成功后，你就可以在 Claude 对话中直接使用 list_tables、describe_table、run_query 等工具，对各个表的记录进行查询和分析了。