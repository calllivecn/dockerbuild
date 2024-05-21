
# 1. 执行备份
# 2. borg prune

# 从环境变量中拿参数
# SSH_REMOTE_REPO_DIR=
# BORG_PATTERNS_FROM=
# BORG_KEY_FILE=

set -e
set -u

#borg create -s --patterns-from $BORG_PATTERNS_FROM $SSH_REMOTE_REPO_DIR::{hostname}-{user}_{now:%Y-%m-%d_%H-%M-%S.%f}
borg create -s --patterns-from $BORG_PATTERNS_FROM $SSH_REMOTE_REPO_DIR::{hostname}-{user}_{now:%Y-%m-%d_%H-%M-%S}

borg prune -s --keep-last 10 $SSH_REMOTE_REPO_DIR

borg compact -s $SSH_REMOTE_REPO_DIR

