# cd www/domains/expressions.club/repo/
git br prod_$(date +%Y%m%d-%H%M)
git reset --hard HEAD\^
git pull origin prod
