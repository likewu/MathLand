SCRIPT_PATH=$(realpath ${BASH_SOURCE})

sudo rm -f $SCRIPT_PATH

#echo $BASH_SOURCE

echo "Welcome to Debian in MathLand!"

if ! type wget >/dev/null 2>&1; then
  sudo apt -y update;
  sudo apt -y install wget;
  sudo apt -y install vim;

  mv /etc/localtime /etc/localtime.bak
  ln -s /usr/share/zoneinfo/Asia/Shanghai /etc/localtime
fi