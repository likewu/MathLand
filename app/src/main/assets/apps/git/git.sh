#! /bin/bash

SCRIPT_PATH=$(realpath ${BASH_SOURCE})
sudo rm -f $SCRIPT_PATH

if [ ! -f /usr/bin/git ]; then
   sudo apt-get update
   sudo DEBIAN_FRONTEND=noninteractive apt-get -y --no-install-recommends install git-gui
fi

if [[ $? != 0 ]]; then
   read -rsp $'An error occurred installing packages, please try again and if it persists provide this log to the developer.\nPress any key to close...\n' -n1 key
   exit
fi

PS3="选择操作:"
options=("Pull 拉取" "Push 推送" "Commit 提交" "创建cdgit" "Quit 退出")

#cd /storage/MathLand
#cd lmr
#git add .
#git commit -m"hh"
#git push
#git gui

select opt in "${options[@]}"
do
  case $opt in
    "Pull 拉取")
      cd /storage/MathLand/lmr
      git pull
      ;;
    "Push 推送")
      cd /storage/MathLand/lmr
      git push
      ;;
    "Commit 提交")
      cd /storage/MathLand/lmr
      git add .
      git commit -m"jj"
      ;;
    "创建cdgit")
      echo cd /storage/MathLand/lmr > "$HOME"/.local/bin/cdgit
      chmod +x "$HOME"/.local/bin/cdgit
      ;;
    "Quit 退出")
      break
      ;;
    *) echo invalid option;;
  esac
done

exit
