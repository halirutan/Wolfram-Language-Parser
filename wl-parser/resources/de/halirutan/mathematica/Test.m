trans = Function[{in},
  Module[{part},
    part = FileNameJoin[FileNameSplit[in][[8 ;; -1]]];
    Rule[in, #] & @@
        Select[ideaFiles, StringMatchQ[#, __ ~~ part] &]
  ]] /@ files;