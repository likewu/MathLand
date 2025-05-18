#! /bin/bash

SCRIPT_PATH=$(realpath ${BASH_SOURCE})
sudo rm -f $SCRIPT_PATH

if [ ! -f "$HOME"/.local/bin/julia ]; then
   #sudo apt-get update
   cpuarch=$(uname -m)
   cpuarch=${cpuarch:-"aarch64"}
   wget https://julialang-s3.julialang.org/bin/linux/aarch64/1.6/julia-1.6.4-linux-${cpuarch}.tar.gz
   tar -xvzf julia-1.6.4-linux-${cpuarch}.tar.gz
   #sudo DEBIAN_FRONTEND=noninteractive apt-get -y install r-base r-base-dev 
   if [[ $? != 0 ]]; then
      read -rsp $'An error occurred installing packages, please try again and if it persists provide this log to the developer.\nPress any key to close...\n' -n1 key
   fi
   rm julia-1.6.4-linux-${cpuarch}.tar.gz
   cd
   mkdir -p .local/bin
   #echo "export PATH=\$HOME/.local/bin:\$PATH" >> ~/.bashrc
   #. ~/.bashrc
   ln -s "$HOME"/julia-1.6.4/bin/julia "$HOME"/.local/bin
   #mkdir $HOME/.termux;
   #echo "extra-keys = [['ESC','/','-','$','HOME','UP','END','PGUP'],['TAB','CTRL','ALT','?','LEFT','DOWN','RIGHT','PGDN']]" > $HOME/.termux/termux.properties
fi
#julia -e 'using Pkg;Pkg.add("Pluto");using Pluto; Pluto.run();'
"$HOME"/.local/bin/julia
exit
